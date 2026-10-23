package com.example.overdex.comms

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Lives with the application, rather than restarting listeners on Activity recreation. */
class TrainerCommsServices private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository = CommsRepository(PreferencesCommsStore(context), scope)
    private val auth: FirebaseAuth?
    private val mutableEmail = MutableStateFlow<String?>(null)
    val email = mutableEmail.asStateFlow()
    val available: Boolean get() = auth != null

    init {
        auth = runCatching {
            val app = FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()
            FirebaseAuth.getInstance(app)
        }.getOrNull()
        auth?.addAuthStateListener { source ->
            val user = source.currentUser?.takeUnless { it.isAnonymous }
            mutableEmail.value = user?.email
            scope.launch {
                if (source.currentUser?.takeUnless { it.isAnonymous }?.uid != user?.uid) return@launch
                repository.activate(user?.uid, user?.let {
                    FirebaseCommsBackend(it.uid, source, FirebaseFirestore.getInstance())
                })
            }
        }
    }

    suspend fun signIn(email: String, password: String, create: Boolean) {
        val source = requireNotNull(auth) { "Trainer Comms is not configured in this build." }
        if (create) source.createUserWithEmailAndPassword(email.trim(), password).await()
        else source.signInWithEmailAndPassword(email.trim(), password).await()
    }
    suspend fun resetPassword(email: String) {
        requireNotNull(auth) { "Trainer Comms is not configured in this build." }
            .sendPasswordResetEmail(email.trim()).await()
    }
    suspend fun signOut() {
        // Hide account history immediately, then clear the SDK session.
        repository.activate(null, null)
        auth?.signOut()
    }
    suspend fun reconnect() {
        val source = requireNotNull(auth)
        val user = requireNotNull(source.currentUser).also { check(!it.isAnonymous) }
        repository.activate(user.uid, FirebaseCommsBackend(user.uid, source, FirebaseFirestore.getInstance()))
    }
    companion object {
        @Volatile private var instance: TrainerCommsServices? = null
        fun get(context: Context): TrainerCommsServices = instance ?: synchronized(this) {
            instance ?: TrainerCommsServices(context.applicationContext).also { instance = it }
        }
    }
}
