package com.riwt.messenger

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    private lateinit var db: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: ChatListAdapter
    private lateinit var emptyState: TextView

    private var myUid: String = ""
    private var allUsers: Map<String, User> = emptyMap()
    private var authReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance(
            "https://riwt-12863-default-rtdb.europe-west1.firebasedatabase.app"
        ).reference

        emptyState = findViewById(R.id.emptyState)

        val recycler = findViewById<RecyclerView>(R.id.chatList)
        adapter = ChatListAdapter { partnerId, partnerName ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("partnerId", partnerId)
            intent.putExtra("partnerName", partnerName)
            startActivity(intent)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<ImageButton>(R.id.settingsBtn).setOnClickListener {
            Toast.makeText(this, "⚙️ Настройки в разработке", Toast.LENGTH_SHORT).show()
        }

        val searchInput = findViewById<EditText>(R.id.searchInput)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (authReady) filterAndShow(s?.toString()?.trim() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        db.child(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val online = s.getValue(Boolean::class.java) == true
                findViewById<TextView>(R.id.connectionStatus).text =
                    if (online) "🟢 Подключено" else "🔴 Нет соединения"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // === Автологин анонимно ===
        val current = auth.currentUser
        if (current == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    onLoggedIn(result.user?.uid ?: "")
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Ошибка входа: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            onLoggedIn(current.uid)
        }
    }

    private fun onLoggedIn(uid: String) {
        myUid = uid

        // Создаём запись пользователя, если ещё нет
        val userRef = db.child("users").child(uid)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val data = mapOf(
                        "phone" to ("anon_" + uid.take(8)),
                        "displayName" to "Гость",
                        "about" to "",
                        "online" to true,
                        "lastSeen" to System.currentTimeMillis(),
                        "createdAt" to System.currentTimeMillis()
                    )
                    userRef.setValue(data)
                } else {
                    userRef.child("online").setValue(true)
                }
                authReady = true
                loadUsersAndChats()
            }
            override fun onCancelled(error: DatabaseError) {
                authReady = true
                loadUsersAndChats()
            }
        })
    }

    private fun loadUsersAndChats() {
        db.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<User>()
                for (child in snapshot.children) {
                    val u = child.getValue(User::class.java) ?: continue
                    if (child.key == myUid) continue
                    u.uid = child.key ?: ""
                    list.add(u)
                }
                allUsers = list.associateBy { it.uid }
                loadChats()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadChats() {
        db.child("chats")
            .orderByChild("participants/$myUid")
            .equalTo(true)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val chats = mutableListOf<ChatRow>()
                    for (child in snapshot.children) {
                        val chat = child.getValue(Chat::class.java) ?: continue
                        val partnerId = chat.participants?.keys?.firstOrNull { it != myUid } ?: continue
                        val partner = allUsers[partnerId] ?: continue
                        chats.add(
                            ChatRow(
                                partnerId = partnerId,
                                partnerName = partner.displayName ?: partner.phone ?: "Пользователь",
                                lastMessage = chat.lastMessage ?: "Нет сообщений",
                                time = chat.lastMessageTime ?: 0L,
                                avatarUrl = partner.avatarUrl
                            )
                        )
                    }
                    chats.sortByDescending { it.time }
                    adapter.submit(chats)
                    emptyState.visibility = if (chats.isEmpty()) View.VISIBLE else View.GONE
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun filterAndShow(query: String) {
        if (query.length < 2) {
            loadChats()
            return
        }
        val filtered = allUsers.values.filter { u ->
            val phone = u.phone?.replace(" ", "")?.replace("+", "") ?: ""
            val q = query.replace(" ", "").replace("+", "")
            phone.contains(q) ||
            (u.displayName?.lowercase()?.contains(query.lowercase()) == true)
        }
        val rows = filtered.map {
            ChatRow(
                partnerId = it.uid,
                partnerName = it.displayName ?: it.phone ?: "Пользователь",
                lastMessage = it.about ?: "",
                time = 0L,
                avatarUrl = it.avatarUrl
            )
        }
        adapter.submit(rows)
        emptyState.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }
}

// ===== Модели =====

data class User(
    var uid: String = "",
    val phone: String? = null,
    val displayName: String? = null,
    val about: String? = null,
    val avatarUrl: String? = null,
    val online: Boolean = false
)

data class Chat(
    val participants: Map<String, Boolean>? = null,
    val lastMessage: String? = null,
    val lastMessageTime: Long? = null,
    val lastSender: String? = null
)

data class ChatRow(
    val partnerId: String,
    val partnerName: String,
    val lastMessage: String,
    val time: Long,
    val avatarUrl: String?
)
