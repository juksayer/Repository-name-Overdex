package com.example.overdex.tournament

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application lifetime keeps invitations and the current pairing through trips into Pokémon GO. */
class TournamentServices private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository = TournamentRepository(scope)
    private val auth: FirebaseAuth? = runCatching {
        val app = FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance()
        FirebaseAuth.getInstance(app)
    }.getOrNull()
    val available: Boolean get() = auth != null

    init {
        auth?.addAuthStateListener { source ->
            val user = source.currentUser?.takeUnless { it.isAnonymous }
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (source.currentUser?.takeUnless { it.isAnonymous }?.uid != user?.uid) return@launch
                repository.activate(user?.uid, user?.let {
                    FirebaseTournamentBackend(it.uid, source, FirebaseFirestore.getInstance())
                })
            }
        }
    }

    suspend fun reconnect() {
        val source = requireNotNull(auth) { "Tournaments are not configured in this build." }
        val user = source.currentUser?.takeUnless { it.isAnonymous }
        if (user == null) {
            repository.activate(null, null)
            error("Sign in to Trainer Comms first.")
        }
        repository.activate(user.uid, FirebaseTournamentBackend(user.uid, source, FirebaseFirestore.getInstance()))
    }

    companion object {
        @Volatile private var instance: TournamentServices? = null
        fun get(context: Context): TournamentServices = instance ?: synchronized(this) {
            instance ?: TournamentServices(context.applicationContext).also { instance = it }
        }
    }
}
