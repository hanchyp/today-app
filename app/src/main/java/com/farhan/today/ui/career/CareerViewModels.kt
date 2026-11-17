package com.farhan.today.ui.career

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farhan.today.data.db.ApplicationRow
import com.farhan.today.data.db.ApplicationStatus
import com.farhan.today.data.db.ApplicationStatusChangeEntity
import com.farhan.today.data.db.EventMode
import com.farhan.today.data.db.JobApplicationEntity
import com.farhan.today.data.db.JobEventEntity
import com.farhan.today.data.db.JobEventType
import com.farhan.today.data.repo.ApplicationInput
import com.farhan.today.data.repo.CareerRepository
import com.farhan.today.data.repo.EventInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// ------------------------------------------------------------------ Daftar

enum class CareerFilter(val label: String, val statuses: Set<ApplicationStatus>?) {
    ALL("Semua", null),
    SAVED("Tersimpan", setOf(ApplicationStatus.SAVED)),
    APPLIED("Apply", setOf(ApplicationStatus.APPLIED)),
    TEST("Tes", setOf(ApplicationStatus.CODING_TEST)),
    INTERVIEW("Interview", setOf(ApplicationStatus.INTERVIEW)),
    OFFER("Offer", setOf(ApplicationStatus.OFFER)),
    CLOSED("Ditolak", setOf(ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN));

    fun matches(status: ApplicationStatus) = statuses == null || status in statuses
}

data class CareerListState(
    val loading: Boolean = true,
    val filter: CareerFilter = CareerFilter.ALL,
    val query: String = "",
    val counts: Map<CareerFilter, Int> = emptyMap(),
    val rows: List<ApplicationRow> = emptyList(),
    val total: Int = 0,
)

class CareerListViewModel(repo: CareerRepository) : ViewModel() {
    private val filter = MutableStateFlow(CareerFilter.ALL)
    private val query = MutableStateFlow("")

    val state: StateFlow<CareerListState> = combine(repo.applicationsWithNextEvent(), filter, query) { all, f, q ->
        val search = q.trim().lowercase()
        val matchingQuery = all.filter {
            search.isEmpty() || it.application.company.lowercase().contains(search) || it.application.role.lowercase().contains(search)
        }
        CareerListState(
            loading = false, filter = f, query = q,
            counts = CareerFilter.entries.associateWith { cf -> matchingQuery.count { cf.matches(it.application.status) } },
            rows = matchingQuery.filter { f.matches(it.application.status) },
            total = all.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareerListState())

    fun setFilter(f: CareerFilter) { filter.value = f }
    fun setQuery(q: String) { query.value = q }
}

// ------------------------------------------------------------------ Detail

data class ApplicationDetailState(
    val loading: Boolean = true,
    val application: JobApplicationEntity? = null,
    val events: List<JobEventEntity> = emptyList(),
    val nextEvent: JobEventEntity? = null,
    val history: List<ApplicationStatusChangeEntity> = emptyList(),
    val lastUpdated: LocalDate? = null,
    val deleted: Boolean = false,
)

class ApplicationDetailViewModel(
    private val repo: CareerRepository,
    private val id: String,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<ApplicationDetailState> = combine(
        repo.application(id), repo.events(id), repo.statusHistory(id),
    ) { app, events, history ->
        val now = clock.millis()
        ApplicationDetailState(
            loading = false,
            application = app,
            events = events,
            nextEvent = events.firstOrNull { !it.done && it.startAt + it.durationMinutes * 60_000L >= now },
            history = history,
            lastUpdated = app?.let { Instant.ofEpochMilli(it.updatedAt).atZone(clock.zone).toLocalDate() },
            deleted = app == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApplicationDetailState())

    fun changeStatus(to: ApplicationStatus) = viewModelScope.launch { repo.changeStatus(id, to) }
    fun setEventDone(eventId: String, done: Boolean) = viewModelScope.launch { repo.setEventDone(eventId, done) }
    fun deleteStatusHistory(changeId: String) = viewModelScope.launch { repo.deleteStatusHistory(id, changeId) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.deleteApplication(id); onDone() }
}

// ------------------------------------------------------------------ Form lamaran

class ApplicationFormViewModel(private val repo: CareerRepository, private val id: String?) : ViewModel() {
    var company by mutableStateOf("")
    var role by mutableStateOf("")
    var location by mutableStateOf("")
    var url by mutableStateOf("")
    var source by mutableStateOf("")
    var status by mutableStateOf(ApplicationStatus.SAVED)
    var appliedDate by mutableStateOf<LocalDate?>(null)
    var deadlineDate by mutableStateOf<LocalDate?>(null)
    var notes by mutableStateOf("")
    var showErrors by mutableStateOf(false)
    var loaded by mutableStateOf(id == null)
        private set
    var saving by mutableStateOf(false)
        private set

    val isEdit = id != null

    /** Isian saat form dibuka; dibandingkan untuk konfirmasi "Buang perubahan?". */
    private var initial by mutableStateOf(input())
    val dirty: Boolean get() = loaded && !saving && input() != initial

    init {
        if (id != null) viewModelScope.launch {
            repo.application(id).first()?.let {
                company = it.company; role = it.role; location = it.location; url = it.url; source = it.source
                status = it.status; appliedDate = it.appliedDate; deadlineDate = it.deadlineDate; notes = it.notes
            }
            initial = input()
            loaded = true
        }
    }

    private fun input() = ApplicationInput(company, role, location, url, source, status, appliedDate, deadlineDate, notes)

    fun save(onSaved: (String) -> Unit) {
        if (saving) return
        val input = input()
        if (!input.isValid) { showErrors = true; return }
        saving = true
        viewModelScope.launch { onSaved(repo.saveApplication(input, id)) }
    }
}

// ------------------------------------------------------------------ Jadwalkan event

val ReminderOptions: List<Pair<Int?, String>> = listOf(15 to "15 mnt", 30 to "30 mnt", 60 to "1 jam", 1440 to "1 hari", null to "Tidak ada")
val DurationOptions: List<Pair<Int, String>> = listOf(30 to "30 mnt", 60 to "60 mnt", 90 to "90 mnt", 120 to "2 jam")

class ScheduleEventViewModel(
    private val repo: CareerRepository,
    private val applicationId: String,
    private val eventId: String?,
    private val clock: Clock,
    private val defaultReminder: (suspend () -> Int?)? = null,
) : ViewModel() {
    var application by mutableStateOf<JobApplicationEntity?>(null)
        private set
    var type by mutableStateOf(JobEventType.INTERVIEW)
    var title by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now(clock).plusDays(1))
    var time by mutableStateOf(LocalTime.of(10, 0))
    var duration by mutableStateOf(60)
    var mode by mutableStateOf(EventMode.ONLINE)
    var link by mutableStateOf("")
    var reminder by mutableStateOf<Int?>(30)
    var saving by mutableStateOf(false)
        private set

    val isEdit = eventId != null
    var loaded by mutableStateOf(false)
        private set

    private fun snapshot(): List<Any?> = listOf(type, title, date, time, duration, mode, link, reminder)
    private var initial by mutableStateOf(snapshot())
    /** Ada isian yang berubah sejak form dibuka (untuk konfirmasi saat kembali). */
    val dirty: Boolean get() = loaded && !saving && snapshot() != initial

    init {
        viewModelScope.launch {
            application = repo.application(applicationId).first()
            if (eventId == null) defaultReminder?.let { reminder = it() }
            eventId?.let { repo.event(it) }?.let { e ->
                val start = Instant.ofEpochMilli(e.startAt).atZone(clock.zone)
                type = e.type; title = e.title; date = start.toLocalDate(); time = start.toLocalTime().withSecond(0).withNano(0)
                duration = e.durationMinutes; mode = e.mode; link = e.locationOrLink; reminder = e.reminderMinutesBefore
            }
            initial = snapshot()
            loaded = true
        }
    }

    val isInPast: Boolean get() = LocalDateTime.of(date, time).atZone(clock.zone).toInstant().toEpochMilli() < clock.millis()

    /** Simpan lalu kembalikan event + saran kenaikan status (bila ada). */
    fun save(onSaved: (event: JobEventEntity, promoteTo: ApplicationStatus?) -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            val event = repo.saveEvent(
                EventInput(applicationId, type, title, LocalDateTime.of(date, time), duration, mode, link, reminder),
                eventId,
            )
            val promote = application?.let { CareerRepository.shouldOfferPromotion(it.status, type) }
            saving = false
            onSaved(event, promote)
        }
    }

    fun promote(to: ApplicationStatus, then: () -> Unit) = viewModelScope.launch { repo.changeStatus(applicationId, to); then() }

    fun delete(then: () -> Unit) = viewModelScope.launch { eventId?.let { repo.deleteEvent(it) }; then() }
}
