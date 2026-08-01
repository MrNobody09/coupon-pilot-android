package com.couponpilot.mvp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CouponViewModel(private val dao: CouponDao) : ViewModel() {
    val coupons = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun add(coupon: Coupon) = viewModelScope.launch { dao.insert(coupon) }
    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = CouponDatabase.get(this).couponDao()
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CouponViewModel(dao) as T
        })[CouponViewModel::class.java]
        setContent { MaterialTheme { CouponPilotApp(vm) } }
    }
}

@Composable
fun CouponPilotApp(vm: CouponViewModel) {
    val coupons by vm.coupons.collectAsStateWithLifecycle()
    var merchant by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val matches = remember(coupons, merchant, amount, paymentMethod) {
        CouponEngine.rank(coupons, merchant, amount.toDoubleOrNull() ?: 0.0, paymentMethod)
    }
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Coupon Pilot") }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("+") } }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Find the best coupon", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(amount, { amount = it }, label = { Text("Payment amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(paymentMethod, { paymentMethod = it }, label = { Text("Payment method (optional)") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                Button(
                    onClick = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Enable notification access") }
            }
            if (matches.isEmpty()) item { Text("No coupons yet. Add one manually or enable notification access.") }
            items(matches, key = { it.coupon.id }) { match -> CouponCard(match, context, vm::delete) }
        }
    }

    if (showAdd) AddCouponDialog(onDismiss = { showAdd = false }, onAdd = { vm.add(it); showAdd = false })
}

@Composable
private fun CouponCard(match: CouponMatch, context: Context, onDelete: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(match.coupon.merchant, style = MaterialTheme.typography.titleMedium)
                Text(if (match.eligible) "Eligible" else "Not eligible")
            }
            Text(if (match.coupon.discountType == "PERCENT") "${match.coupon.discountValue.toInt()}% off" else "₹${match.coupon.discountValue.toInt()} off")
            Text(match.reason)
            if (match.coupon.code.isNotBlank()) Text("Code: ${match.coupon.code}")
            Text("Source: ${match.coupon.sourceApp}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (match.coupon.code.isNotBlank()) Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("coupon code", match.coupon.code))
                }) { Text("Copy code") }
                TextButton(onClick = { onDelete(match.coupon.id) }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun AddCouponDialog(onDismiss: () -> Unit, onAdd: (Coupon) -> Unit) {
    var merchant by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("PERCENT") }
    var value by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add coupon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant") })
                OutlinedTextField(code, { code = it }, label = { Text("Code") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(type == "PERCENT", { type = "PERCENT" }); Text("Percent")
                    RadioButton(type == "FLAT", { type = "FLAT" }); Text("Flat")
                }
                OutlinedTextField(value, { value = it }, label = { Text("Discount value") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(max, { max = it }, label = { Text("Maximum discount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(min, { min = it }, label = { Text("Minimum spend") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(payment, { payment = it }, label = { Text("Required payment method") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onAdd(Coupon(
                    merchant = merchant.ifBlank { "Any merchant" },
                    code = code.uppercase(),
                    discountType = type,
                    discountValue = value.toDoubleOrNull() ?: 0.0,
                    maximumDiscount = max.toDoubleOrNull(),
                    minimumSpend = min.toDoubleOrNull() ?: 0.0,
                    paymentMethod = payment,
                    expiresAtEpochMillis = null,
                    sourceApp = "Manual",
                    rawText = "Manual entry"
                ))
            }, enabled = value.toDoubleOrNull() != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
