package com.farhan.today.data.db

// Room menyimpan enum sebagai TEXT (nama), jadi urutan enum aman diubah.

enum class MuscleGroup { CHEST, BACK, LEGS, SHOULDERS, ARMS, CORE, FULL_BODY }
enum class Equipment { BARBELL, DUMBBELL, MACHINE, BODYWEIGHT, CABLE, OTHER }
enum class SessionStatus { PLANNED, IN_PROGRESS, DONE, SKIPPED }

enum class ApplicationStatus { SAVED, APPLIED, CODING_TEST, INTERVIEW, OFFER, REJECTED, WITHDRAWN }
enum class JobEventType { CODING_TEST, INTERVIEW, FOLLOW_UP, OTHER }
enum class EventMode { ONLINE, ONSITE }

enum class MoneyKind { EXPENSE, INCOME }
enum class BudgetPeriod { WEEKLY, MONTHLY }
enum class BillRepeat { NONE, MONTHLY, YEARLY }

enum class TaskPriority { LOW, NORMAL, HIGH }
enum class TaskStatus { TODO, DONE }

enum class ReminderSource { JOB_EVENT, BILL, WORKOUT, TASK }
enum class ReminderState { SCHEDULED, FIRED, CANCELLED }
