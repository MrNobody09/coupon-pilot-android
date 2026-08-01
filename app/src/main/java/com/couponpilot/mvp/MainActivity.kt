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

class CouponViewModel(
    private val couponDao: CouponDao,
    private val learningDao: LearningDao
) : ViewModel() {
    val coupons = couponDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val feedback = learningDao.observeFeedback().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val proposals = learningDao.observeProposals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(coupon: Coupon) = viewModelScope.launch { couponDao.insert(coupon) }
    fun delete(id: Long) = viewModelScope.launch { couponDao.delete(id) }

    fun submitFeedback(item: CouponFeedback) = viewModelScope.launch {
        learningDao.insertFeedback(item)
        generateProposals(feedback.value + item)
    }

    fun analyseNow() = viewModelScope.launch { generateProposals(feedback.value) }

    private suspend fun generateProposals(rows: List<CouponFeedback>) {
        LearningEngine.analyse(rows).forEach { proposal ->
            if (learningDao.existingProposalCount(proposal.proposalType, proposal.rulePayload) == 0) {
                learningDao.insertProposal(proposal)
            }
        }
    }

    fun reviewProposal(id: Long, approved: Boolean) = viewModelScope.launch {
        learningDao.reviewProposal(id, if (approved) "APPROVED" else "REJECTED")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = CouponDatabase.get(this)
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CouponViewModel(db.couponDao(), db.learningDao()) as T
        })[CouponViewModel::class.java]
        setContent { MaterialTheme { CouponPilotApp(vm) } }
    }
}

@Composable
fun CouponPilotApp(vm: CouponViewModel) {
    val coupons by vm.coupons.collectAsStateWithLifecycle()
    val proposals by vm.proposals.collectAsStateWithLifecycle()
    var merchant by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var feedbackTarget by remember { mutableStateOf<Coupon?>(null) }
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
            items(matches, key = { it.coupon.id }) { match ->
                CouponCard(match, context, vm::delete, onFeedback = { feedbackTarget = it })
            }

            item {
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Learning proposals", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = vm::analyseNow) { Text("Analyse feedback") }
                }
                Text("Suggestions are never activated without your approval.", style = MaterialTheme.typography.bodySmall)
            }
            if (proposals.isEmpty()) item { Text("No proposals yet. At least a few feedback records are needed.") }
            items(proposals, key = { "proposal-${it.id}" }) { proposal ->
                ProposalCard(proposal, vm::reviewProposal)
            }
        }
    }

    if (showAdd) AddCouponDialog(onDismiss = { showAdd = false }, onAdd = { vm.add(it); showAdd = false })
    feedbackTarget?.let { coupon ->
        FeedbackDialog(
            coupon = coupon,
            amount = amount.toDoubleOrNull() ?: 0.0,
            paymentMethod = paymentMethod,
            onDismiss = { feedbackTarget = null },
            onSubmit = { vm.submitFeedback(it); feedbackTarget = null }
        )
    }
}

@Composable
private fun CouponCard(
    match: CouponMatch,
    context: Context,
    onDelete: (Long) -> Unit,
    onFeedback: (Coupon) -> Unit
) {
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
                TextButton(onClick = { onFeedback(match.coupon) }) { Text("Give feedback") }
                TextButton(onClick = { onDelete(match.coupon.id) }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun FeedbackDialog(
    coupon: Coupon,
    amount: Double,
    paymentMethod: String,
    onDismiss: () -> Unit,
    onSubmit: (CouponFeedback) -> Unit
) {
    var outcome by remember { mutableStateOf("SUCCESS") }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recommendation feedback") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${coupon.merchant} · ${coupon.code.ifBlank { "No code" }}")
                listOf("SUCCESS" to "Worked", "FAILED" to "Did not work", "NOT_BEST" to "A better coupon existed").forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = outcome == value, onClick = { outcome = value })
                        Text(label)
                    }
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("Reason or details") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onSubmit(CouponFeedback(
                    couponId = coupon.id,
                    merchant = coupon.merchant,
                    sourceApp = coupon.sourceApp,
                    outcome = outcome,
                    reason = reason,
                    transactionAmount = amount,
                    paymentMethod = paymentMethod
                ))
            }) { Text("Submit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ProposalCard(proposal: ImprovementProposal, onReview: (Long, Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(proposal.title, style = MaterialTheme.typography.titleMedium)
                Text(proposal.status)
            }
            Text(proposal.description)
            Text("Evidence: ${proposal.evidence}", style = MaterialTheme.typography.bodySmall)
            Text("Confidence: ${(proposal.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            if (proposal.status == "PENDING") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onReview(proposal.id, true) }) { Text("Approve") }
                    OutlinedButton(onClick = { onReview(proposal.id, false) }) { Text("Reject") }
                }
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
