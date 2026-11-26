package com.mailshelf.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val vault = Vault(applicationContext)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { Shelf(vault) }
            }
        }
    }
}

@Composable
private fun Shelf(vault: Vault) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(emptyList<Account>()) }
    var messages by remember { mutableStateOf(emptyList<MailItem>()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Loading accounts…") }
    var vaultReady by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var add by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<Account?>(null) }
    var opened by remember { mutableStateOf<MailItem?>(null) }
    var body by remember { mutableStateOf("") }
    var bodyJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(vault) {
        try {
            accounts = withContext(Dispatchers.IO) { vault.accounts() }
            vaultReady = true
            status = if (accounts.isEmpty()) "Add your first Gmail account." else "Ready. Tap Refresh to load recent messages."
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { status = "Could not unlock local vault. Existing data has not been overwritten." }
    }

    fun refresh() {
        if (busy || !vaultReady) return
        val snapshot = accounts
        if (snapshot.isEmpty()) { status = "Add an account first."; return }
        busy = true
        scope.launch {
            var failed = 0
            var completed = 0
            val result = mutableListOf<MailItem>()
            try {
                for (batch in snapshot.chunked(4)) {
                    val outcomes = coroutineScope {
                        batch.map { a -> async(Dispatchers.IO) {
                            try { Gmail.recent(a.email, vault.password(a.email)) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { null }
                        } }.awaitAll()
                    }
                    for (outcome in outcomes) {
                        if (outcome == null) failed++ else result.addAll(outcome)
                    }
                    completed += batch.size
                    messages = result.sortedByDescending { it.time }
                    status = "Refreshed $completed / ${snapshot.size} accounts; $failed failed"
                }
                status = "${messages.size} recent messages · $failed account(s) failed"
            } finally { busy = false }
        }
    }
    fun closeMessage() {
        bodyJob?.cancel()
        opened = null
        body = ""
    }
    fun openMessage(item: MailItem) {
        bodyJob?.cancel()
        opened = item
        body = "Loading message…"
        bodyJob = scope.launch {
            try {
                val content = withContext(Dispatchers.IO) { Gmail.body(item, vault.password(item.email)) }
                if (opened?.key == item.key) body = content
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (opened?.key == item.key) body = "Unable to load this message. Refresh and try again." }
        }
    }
    BackHandler(opened != null) { closeMessage() }
    if (opened != null) {
        val item = opened!!
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            TextButton(onClick = { closeMessage() }) { Text("Back to inbox") }
            Text(item.subject, style = MaterialTheme.typography.headlineSmall)
            Text(item.sender, style = MaterialTheme.typography.bodyMedium)
            Text(item.email, style = MaterialTheme.typography.labelMedium)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            LazyColumn(Modifier.weight(1f)) { item { Text(body) } }
        }
    } else {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Text("MailShelf", style = MaterialTheme.typography.headlineLarge)
            Text("${accounts.size} connected accounts · Read only", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { add = true }, enabled = vaultReady && !busy) { Text("Add account") }
                OutlinedButton(onClick = { manage = true }, enabled = vaultReady && !busy) { Text("Accounts") }
                TextButton(onClick = { refresh() }, enabled = vaultReady && !busy) { Text("Refresh") }
            }
            OutlinedTextField(query, { query = it }, label = { Text("Search recent sender or subject") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            TextButton(onClick = { manage = true }, enabled = !busy) {
                Text("Showing: " + (accounts.find { it.email == selected }?.label ?: "All inboxes"))
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            val filtered = remember(messages, selected, query) {
                messages.filter { m -> (selected == null || m.email == selected) &&
                    (m.sender.contains(query, true) || m.subject.contains(query, true) || m.email.contains(query, true)) }
            }
            LazyColumn(Modifier.weight(1f)) {
                if (filtered.isEmpty()) item { Text("No recent messages to display.", modifier = Modifier.padding(16.dp)) }
                items(filtered, key = { it.key }) { m ->
                    ListItem(
                        headlineContent = { Text((if (m.unread) "• " else "") + m.subject, maxLines = 2) },
                        supportingContent = { Text(m.sender + "\n" + (accounts.find { it.email == m.email }?.label ?: m.email), maxLines = 3) },
                        trailingContent = { Text(if (m.time > 0) DateFormat.getDateInstance(DateFormat.SHORT).format(Date(m.time)) else "") },
                        modifier = Modifier.fillMaxWidth().then(Modifier.clickable { openMessage(m) })
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (add) {
        var email by remember { mutableStateOf("") }
        var label by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { if (!busy) add = false },
            title = { Text("Connect Gmail") },
            text = {
                Column {
                    Text("Use a Google app password, not your normal password. Enable 2-Step Verification first.")
                    OutlinedTextField(email, { email = it }, label = { Text("Gmail address") }, singleLine = true,
                        enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    OutlinedTextField(label, { label = it }, label = { Text("Account name (optional)") }, singleLine = true, enabled = !busy)
                    OutlinedTextField(password, { password = it }, label = { Text("App password") }, singleLine = true,
                        enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val address = email.trim().lowercase(Locale.ROOT)
                    val secret = password.filterNot { it.isWhitespace() }
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches() || secret.length != 16) {
                        error = "Enter a valid email and 16-character app password."
                    } else {
                        val a = Account(address, label.trim().ifEmpty { address })
                        busy = true
                        error = ""
                        scope.launch {
                            try {
                                accounts = withContext(Dispatchers.IO) {
                                    Gmail.verify(a.email, secret)
                                    vault.put(a, secret)
                                    vault.accounts()
                                }
                                password = ""
                                add = false
                                status = "Account connected. Tap Refresh."
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "Connection or storage failed. Check app password eligibility and network." }
                            finally { busy = false }
                        }
                    }
                }) { Text("Connect") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { add = false }) { Text("Cancel") } })
    }
    if (manage) {
        AlertDialog(onDismissRequest = { manage = false },
            title = { Text("Accounts") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item { TextButton(onClick = { selected = null; manage = false }) { Text("All inboxes") } }
                    items(accounts, key = { it.email }) { a ->
                        ListItem(headlineContent = { Text(a.label) }, supportingContent = { Text(a.email) },
                            trailingContent = { TextButton(onClick = { remove = a; manage = false }) { Text("Remove") } },
                            modifier = Modifier.clickable { selected = a.email; manage = false })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { manage = false }) { Text("Close") } })
    }
    remove?.let { a ->
        AlertDialog(onDismissRequest = { if (!busy) remove = null }, title = { Text("Remove account?") },
            text = { Text("Remove ${a.email} from MailShelf only. Gmail messages will not be deleted.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        accounts = withContext(Dispatchers.IO) { vault.remove(a.email); vault.accounts() }
                        messages = messages.filterNot { it.email == a.email }
                        if (selected == a.email) selected = null
                        status = "Account removed."
                        remove = null
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { status = "Could not remove account."; remove = null }
                    finally { busy = false }
                }
            }) { Text("Remove") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { remove = null }) { Text("Cancel") } })
    }
}
