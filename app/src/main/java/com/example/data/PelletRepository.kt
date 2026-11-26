package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CalculationMethod
import com.example.model.PelletEntry
import com.example.model.PelletStockBatch
import com.example.model.PelletUnit
import com.example.model.UserSettings
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PelletRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Użytkownik musi być zalogowany przez Google.")
    }

    fun observeUserSettings(userId: String): Flow<UserSettings> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Niezgodność identyfikatora użytkownika." }
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: UserSettings(userId = uid)
                    } else {
                        UserSettings(userId = uid)
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeEntries(userId: String): Flow<List<PelletEntry>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Niezgodność identyfikatora użytkownika." }
        val colRef = db.collection("users").document(uid).collection("entries")
        emitAll(
            colRef
                .orderBy("dateEpochDay", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PelletEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveUserSettings(
        bagCapacityKg: Double,
        calculationMethod: CalculationMethod
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            require(bagCapacityKg in 1.0..100.0) { "Pojemność worka musi wynosić od 1 do 100 kg." }
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path

            db.runTransaction { transaction ->
                val existing = transaction.get(docRef)
                if (!existing.exists()) {
                    val createPayload = mapOf(
                        "userId" to uid,
                        "bagCapacityKg" to bagCapacityKg,
                        "calculationMethod" to calculationMethod.firestoreValue,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    transaction.set(docRef, createPayload)
                } else {
                    val updatePayload = mapOf(
                        "bagCapacityKg" to bagCapacityKg,
                        "calculationMethod" to calculationMethod.firestoreValue,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    transaction.update(docRef, updatePayload)
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addEntry(
        dateEpochDay: Long,
        inputUnit: PelletUnit,
        inputValue: Double,
        bagCapacityKgAtEntry: Double,
        weightKg: Double,
        note: String?
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/entries"
        return try {
            val uid = requireUserId()
            require(dateEpochDay in -36500L..100000L) { "Nieprawidłowa data wsypu." }
            require(inputValue > 0.0 && inputValue <= 10000.0) { "Ilość musi być większa od 0 i mniejsza od 10 000." }
            require(bagCapacityKgAtEntry in 1.0..100.0) { "Nieprawidłowa pojemność worka." }
            require(weightKg > 0.0 && weightKg <= 10000.0) { "Masa musi być większa od 0 i mniejsza od 10 000 kg." }
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(120)

            val docRef = db.collection("users").document(uid).collection("entries").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "dateEpochDay" to dateEpochDay,
                "inputUnit" to inputUnit.firestoreValue,
                "inputValue" to inputValue,
                "bagCapacityKgAtEntry" to bagCapacityKgAtEntry,
                "weightKg" to weightKg,
                "note" to cleanNote,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateEntry(
        entryId: String,
        dateEpochDay: Long,
        inputUnit: PelletUnit,
        inputValue: Double,
        bagCapacityKgAtEntry: Double,
        weightKg: Double,
        note: String?
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/entries/$entryId"
        return try {
            val uid = requireUserId()
            require(entryId.isNotBlank()) { "Brak identyfikatora wpisu." }
            require(dateEpochDay in -36500L..100000L) { "Nieprawidłowa data wsypu." }
            require(inputValue > 0.0 && inputValue <= 10000.0) { "Ilość musi być większa od 0 i mniejsza od 10 000." }
            require(bagCapacityKgAtEntry in 1.0..100.0) { "Nieprawidłowa pojemność worka." }
            require(weightKg > 0.0 && weightKg <= 10000.0) { "Masa musi być większa od 0 i mniejsza od 10 000 kg." }
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(120)

            val docRef = db.collection("users").document(uid).collection("entries").document(entryId)
            docPath = docRef.path

            val updates = mapOf<String, Any>(
                "dateEpochDay" to dateEpochDay,
                "inputUnit" to inputUnit.firestoreValue,
                "inputValue" to inputValue,
                "bagCapacityKgAtEntry" to bagCapacityKgAtEntry,
                "weightKg" to weightKg,
                "note" to (cleanNote ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteEntry(entryId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/entries/$entryId"
        return try {
            val uid = requireUserId()
            require(entryId.isNotBlank()) { "Brak identyfikatora wpisu." }
            val docRef = db.collection("users").document(uid).collection("entries").document(entryId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeStockBatches(userId: String): Flow<List<PelletStockBatch>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Niezgodność identyfikatora użytkownika." }
        val colRef = db.collection("users").document(uid).collection("stock_batches")
        emitAll(
            colRef
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PelletStockBatch::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun addStockBatch(
        bagsCount: Double,
        bagCapacityKg: Double,
        pricePerBagPln: Double,
        purchaseDateEpochDay: Long,
        purchasePlace: String?,
        pelletType: String?,
        note: String?
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/stock_batches"
        return try {
            val uid = requireUserId()
            val totalKg = bagsCount * bagCapacityKg
            val totalCostPln = bagsCount * pricePerBagPln
            require(bagsCount > 0.0 && bagsCount <= 10000.0) { "Liczba worków musi wynosić od 1 do 10 000." }
            require(bagCapacityKg in 1.0..100.0) { "Pojemność worka musi wynosić od 1 do 100 kg." }
            require(totalKg > 0.0 && totalKg <= 100000.0) { "Masa partii przekracza limit 100 000 kg." }
            require(pricePerBagPln >= 0.0 && pricePerBagPln <= 100000.0) { "Nieprawidłowa cena za worek." }
            require(totalCostPln >= 0.0 && totalCostPln <= 10000000.0) { "Nieprawidłowy koszt całkowity." }
            require(purchaseDateEpochDay in -36500L..100000L) { "Nieprawidłowa data zakupu partii." }
            val cleanPlace = purchasePlace?.trim()?.takeIf { it.isNotEmpty() }?.take(120)
            val cleanType = pelletType?.trim()?.takeIf { it.isNotEmpty() }?.take(120)
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(120)

            val docRef = db.collection("users").document(uid).collection("stock_batches").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "bagsCount" to bagsCount,
                "bagCapacityKg" to bagCapacityKg,
                "totalKg" to totalKg,
                "pricePerBagPln" to pricePerBagPln,
                "totalCostPln" to totalCostPln,
                "purchaseDateEpochDay" to purchaseDateEpochDay,
                "purchasePlace" to cleanPlace,
                "pelletType" to cleanType,
                "note" to cleanNote,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteStockBatch(batchId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/stock_batches/$batchId"
        return try {
            val uid = requireUserId()
            require(batchId.isNotBlank()) { "Brak identyfikatora partii." }
            val docRef = db.collection("users").document(uid).collection("stock_batches").document(batchId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
