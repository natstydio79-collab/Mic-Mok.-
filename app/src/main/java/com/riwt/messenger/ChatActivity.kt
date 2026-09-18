package com.riwt.messenger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

data class ChatMessage(
    val id: String = "",
    val type: String = "text",
    val text: String? = null,
    val imageUrl: String? = null,
    val senderId: String = "",
    val senderName: String? = null,
    val timestamp: Long = 0
)

class ChatActivity : AppCompatActivity() {

    private lateinit var db: DatabaseReference
    private lateinit var adapter: MessagesAdapter
    private var chatId: String = ""
    private var myUid: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val partnerId = intent.getStringExtra("partnerId") ?: return
        val partnerName = intent.getStringExtra("partnerName") ?: "Пользователь"

        chatId = listOf(myUid, partnerId).sorted().joinToString("_")

        findViewById<TextView>(R.id.partnerName).text = partnerName

        db = FirebaseDatabase.getInstance(
            "https://riwt-12863-default-rtdb.europe-west1.firebasedatabase.app"
        ).reference

        val recycler = findViewById<RecyclerView>(R.id.messagesRecycler)
        adapter = MessagesAdapter(myUid)
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener { finish() }

        val input = findViewById<EditText>(R.id.messageInput)
        findViewById<ImageButton>(R.id.sendBtn).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                sendTextMessage(text)
                input.setText("")
            }
        }

        listenMessages()
    }

    private fun sendTextMessage(text: String) {
        val msgRef = db.child("messages").child(chatId).push()
        val data = mapOf(
            "type" to "text",
            "text" to text,
            "senderId" to myUid,
            "senderName" to (FirebaseAuth.getInstance().currentUser?.displayName ?: ""),
            "timestamp" to System.currentTimeMillis()
        )
        msgRef.setValue(data)

        val partnerId = chatId.split("_").first { it != myUid }
        db.child("chats").child(chatId).updateChildren(
            mapOf(
                "lastMessage" to text,
                "lastMessageTime" to System.currentTimeMillis(),
                "lastSender" to myUid,
                "participants/$myUid" to true,
                "participants/$partnerId" to true
            )
        )
    }

    private fun listenMessages() {
        db.child("messages").child(chatId).limitToLast(50)
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, prev: String?) {
                    val msg = snapshot.getValue(ChatMessage::class.java) ?: return
                    adapter.add(msg.copy(id = snapshot.key ?: ""))
                    findViewById<RecyclerView>(R.id.messagesRecycler)
                        .scrollToPosition(adapter.itemCount - 1)
                }
                override fun onChildChanged(snapshot: DataSnapshot, prev: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, prev: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}

class MessagesAdapter(private val myUid: String) :
    RecyclerView.Adapter<MessagesAdapter.VH>() {

    private val items = mutableListOf<ChatMessage>()

    fun add(msg: ChatMessage) {
        items.add(msg)
        notifyItemInserted(items.size - 1)
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position].senderId == myUid) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 1) R.layout.item_message_me
                     else R.layout.item_message_other
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        holder.text.text = m.text ?: ""
        if (m.type == "image" && m.imageUrl != null) {
            holder.image.visibility = View.VISIBLE
            Glide.with(holder.image).load(m.imageUrl).into(holder.image)
        } else {
            holder.image.visibility = View.GONE
        }
        holder.meta.text = android.text.format.DateFormat
            .format("HH:mm", m.timestamp).toString()
    }

    override fun getItemCount() = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.text)
        val image: ImageView = v.findViewById(R.id.image)
        val meta: TextView = v.findViewById(R.id.meta)
    }
}
