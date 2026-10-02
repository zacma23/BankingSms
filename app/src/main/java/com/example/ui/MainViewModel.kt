package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.FinEthioApp
import com.example.data.ProcessingResult
import com.example.data.TransactionRepository
import com.example.model.DashboardSummary
import com.example.model.FinancialProvider
import com.example.model.ParsedTransaction
import com.example.model.TransactionEntity
import com.example.parser.ProviderConfig
import com.example.parser.ProviderRegistry
import com.example.service.ChatMessage
import com.example.service.GeminiService
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class SortOrder(val displayName: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    HIGHEST_AMOUNT("Highest Amount"),
    LOWEST_AMOUNT("Lowest Amount")
}

data class FilterState(
    val query: String = "",
    val provider: String = "ALL",
    val type: String = "ALL",
    val category: String = "ALL",
    val sort: SortOrder = SortOrder.NEWEST
)

data class SimulatorResultState(
    val parsedTransaction: ParsedTransaction? = null,
    val isDuplicate: Boolean = false,
    val savedEntity: TransactionEntity? = null,
    val errorMessage: String? = null
)

class MainViewModel(
    private val repository: TransactionRepository = FinEthioApp.instance.repository
) : ViewModel() {

    private val geminiService = GeminiService()

    // 1. Transactions State
    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. Dashboard Summary
    val dashboardSummary: StateFlow<DashboardSummary> = allTransactions
        .combine(MutableStateFlow(Unit)) { txList, _ ->
            repository.calculateSummary(txList)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    // 3. Search & Filtering
    val searchQuery = MutableStateFlow("")
    val selectedProviderFilter = MutableStateFlow("ALL")
    val selectedTypeFilter = MutableStateFlow("ALL")
    val selectedCategoryFilter = MutableStateFlow("ALL")
    val selectedSortOrder = MutableStateFlow(SortOrder.NEWEST)

    private val filterState = combine(
        searchQuery,
        selectedProviderFilter,
        selectedTypeFilter,
        selectedCategoryFilter,
        selectedSortOrder
    ) { query, provider, type, category, sort ->
        FilterState(query, provider, type, category, sort)
    }

    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions,
        filterState
    ) { list, filter ->
        list.filter { tx ->
            val matchesQuery = filter.query.isBlank() ||
                    tx.description?.contains(filter.query, ignoreCase = true) == true ||
                    tx.referenceNumber?.contains(filter.query, ignoreCase = true) == true ||
                    tx.sender?.contains(filter.query, ignoreCase = true) == true ||
                    tx.receiver?.contains(filter.query, ignoreCase = true) == true ||
                    tx.provider.contains(filter.query, ignoreCase = true) ||
                    tx.amount.toString().contains(filter.query)

            val matchesProvider = filter.provider == "ALL" || tx.provider.equals(filter.provider, ignoreCase = true)
            val matchesType = filter.type == "ALL" || tx.transactionType.equals(filter.type, ignoreCase = true)
            val matchesCategory = filter.category == "ALL" || tx.category.equals(filter.category, ignoreCase = true)

            matchesQuery && matchesProvider && matchesType && matchesCategory
        }.sortedWith { a, b ->
            when (filter.sort) {
                SortOrder.NEWEST -> b.createdAt.compareTo(a.createdAt)
                SortOrder.OLDEST -> a.createdAt.compareTo(b.createdAt)
                SortOrder.HIGHEST_AMOUNT -> b.amount.compareTo(a.amount)
                SortOrder.LOWEST_AMOUNT -> a.amount.compareTo(b.amount)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. SMS Parser Simulator State
    private val _simulatorResult = MutableStateFlow<SimulatorResultState?>(null)
    val simulatorResult: StateFlow<SimulatorResultState?> = _simulatorResult.asStateFlow()

    fun simulateSmsParsing(sender: String, message: String, saveToDb: Boolean) {
        viewModelScope.launch {
            val parsed = ProviderRegistry.processMessage(sender, message, System.currentTimeMillis())
            if (parsed == null) {
                _simulatorResult.value = SimulatorResultState(
                    errorMessage = "Unable to parse this message. Please check the format or verify provider rules."
                )
                return@launch
            }

            if (saveToDb) {
                val res = repository.processIncomingMessage(
                    sender = sender,
                    message = message,
                    timestampMillis = System.currentTimeMillis(),
                    source = "SIMULATOR"
                )
                when (res) {
                    is ProcessingResult.Success -> {
                        _simulatorResult.value = SimulatorResultState(
                            parsedTransaction = parsed,
                            isDuplicate = res.isDuplicate,
                            savedEntity = res.transaction
                        )
                    }
                    is ProcessingResult.Failure -> {
                        _simulatorResult.value = SimulatorResultState(
                            parsedTransaction = parsed,
                            errorMessage = res.reason
                        )
                    }
                }
            } else {
                _simulatorResult.value = SimulatorResultState(
                    parsedTransaction = parsed,
                    isDuplicate = false,
                    savedEntity = null
                )
            }
        }
    }

    fun clearSimulatorResult() {
        _simulatorResult.value = null
    }

    // 5. Providers Management State
    private val _providerConfigs = MutableStateFlow<List<ProviderConfig>>(ProviderRegistry.getAllConfigs())
    val providerConfigs: StateFlow<List<ProviderConfig>> = _providerConfigs.asStateFlow()

    fun toggleProvider(provider: FinancialProvider, isEnabled: Boolean) {
        ProviderRegistry.toggleProvider(provider, isEnabled)
        _providerConfigs.value = ProviderRegistry.getAllConfigs()
    }

    // 6. Gemini Financial AI Chat
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "model",
                text = "Selam! I am FinEthiopia AI. I can analyze your Telebirr & bank spending, explain transfer fees, help you budget in ETB, and find nearby CBE branches or Telebirr agents. Ask me anything!"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun sendChatMessage(text: String, enableMaps: Boolean = false) {
        if (text.isBlank() || _isChatLoading.value) return
        val userMsg = ChatMessage(sender = "user", text = text)
        val currentHistory = _chatMessages.value
        _chatMessages.value = currentHistory + userMsg

        _isChatLoading.value = true
        viewModelScope.launch {
            val result = geminiService.sendMessage(
                history = currentHistory,
                userMessage = text,
                enableMaps = enableMaps
            )
            _isChatLoading.value = false
            result.onSuccess { modelMsg ->
                _chatMessages.value = _chatMessages.value + modelMsg
            }.onFailure { err ->
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    sender = "model",
                    text = "Sorry, I could not complete the request: ${err.message}"
                )
            }
        }
    }

    // 7. Manual Add & Delete
    fun addManualTransaction(
        provider: String,
        amount: Double,
        type: String,
        category: String,
        account: String,
        receiver: String?,
        sender: String?,
        referenceNumber: String?,
        description: String?
    ) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val entity = TransactionEntity(
                id = id,
                provider = provider,
                account = account.ifBlank { "Manual Entry" },
                transactionType = type,
                category = category,
                amount = amount,
                currency = "ETB",
                sender = sender,
                receiver = receiver,
                referenceNumber = referenceNumber,
                description = description ?: "$type from $provider",
                transactionDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
                transactionTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date()),
                balanceAfterTransaction = null,
                rawMessage = "Manually entered transaction",
                source = "MANUAL",
                isVerified = true,
                fingerprint = "manual:$id"
            )
            repository.insertTransaction(entity)
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
        }
    }

    fun clearAllTransactions() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    // 8. Auth State
    private val _currentUser = MutableStateFlow<FirebaseUser?>(
        try { Firebase.auth.currentUser } catch (e: Throwable) { null }
    )
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        try {
            Firebase.auth.addAuthStateListener { auth ->
                _currentUser.value = auth.currentUser
            }
        } catch (e: Throwable) {
            // Ignored if uninitialized or offline
        }
    }
}
