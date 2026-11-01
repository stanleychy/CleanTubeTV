package io.github.stanleychy.cleantubetv.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.stanleychy.cleantubetv.R
import io.github.stanleychy.cleantubetv.app
import io.github.stanleychy.cleantubetv.yt.FeedItemsMapper
import io.github.stanleychy.cleantubetv.yt.FeedParser

class MainActivity : Activity() {
    private enum class Tab(val browseId: String?) { HOME("default"), SUBSCRIPTIONS("FEsubscriptions"), ACCOUNT(null) }

    private lateinit var status: TextView
    private lateinit var grid: RecyclerView
    private lateinit var tabs: Map<Tab, TextView>
    private lateinit var adapter: FeedAdapter

    private var current = Tab.HOME
    private var continuation: String? = null
    private var loading = false
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        grid = findViewById(R.id.grid)
        tabs = mapOf(
            Tab.HOME to findViewById(R.id.tab_home),
            Tab.SUBSCRIPTIONS to findViewById(R.id.tab_subs),
            Tab.ACCOUNT to findViewById(R.id.tab_account),
        )
        tabs.forEach { (tab, view) -> view.setOnClickListener { select(tab) } }

        adapter = FeedAdapter(
            onClick = { v -> startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_ID, v.id).putExtra(PlayerActivity.EXTRA_TITLE, v.title)) },
            onNearEnd = { loadMore() },
        )
        val lm = GridLayoutManager(this, COLUMNS)
        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (adapter.isHeader(position)) COLUMNS else 1
        }
        grid.layoutManager = lm
        grid.adapter = adapter
        select(Tab.HOME)
    }

    override fun onResume() {
        super.onResume()
        // Refresh after sign-in or sign-out.
        if (current == Tab.ACCOUNT) showAccount()
    }

    private fun select(tab: Tab) {
        current = tab
        tabs.forEach { (t, v) -> v.isSelected = t == tab }
        generation++
        continuation = null
        loading = false
        adapter.set(emptyList())
        if (tab == Tab.ACCOUNT) showAccount() else load(tab, null)
    }

    private fun showAccount() {
        grid.visibility = View.GONE
        status.visibility = View.VISIBLE
        val signedIn = app.auth.isSignedIn
        status.text = getString(if (signedIn) R.string.account_signed_in else R.string.account_signed_out)
        status.isFocusable = true
        status.setOnClickListener {
            if (app.auth.isSignedIn) {
                app.auth.signOut()
                showAccount()
            } else {
                startActivity(Intent(this, SignInActivity::class.java))
            }
        }
    }

    private fun load(tab: Tab, token: String?) {
        if (loading) return
        if (!app.auth.isSignedIn) {
            grid.visibility = View.GONE
            status.visibility = View.VISIBLE
            status.isFocusable = false
            status.text = getString(R.string.sign_in_first)
            return
        }
        loading = true
        val gen = generation
        if (token == null) {
            status.visibility = View.VISIBLE
            status.isFocusable = false
            status.text = getString(R.string.loading)
        }
        app.io.execute {
            val result = runCatching {
                val resp = app.innerTube.browse(tab.browseId, token)
                FeedParser.parse(resp)
            }
            runOnUiThread {
                if (gen != generation) return@runOnUiThread
                loading = false
                result.onSuccess { page ->
                    continuation = page.continuation
                    val items = FeedItemsMapper.toItems(page)
                    if (token == null) {
                        if (items.isEmpty()) {
                            status.text = getString(R.string.empty_feed)
                        } else {
                            status.visibility = View.GONE
                            grid.visibility = View.VISIBLE
                            adapter.set(items)
                            grid.post { grid.getChildAt(1)?.requestFocus() ?: grid.getChildAt(0)?.requestFocus() }
                        }
                    } else {
                        adapter.append(items)
                    }
                }.onFailure { e ->
                    Log.w(TAG, "browse failed", e)
                    if (token == null) status.text = getString(R.string.load_error, e.message)
                }
            }
        }
    }

    private fun loadMore() {
        val token = continuation ?: return
        if (current == Tab.ACCOUNT) return
        continuation = null
        load(current, token)
    }

    private companion object {
        const val TAG = "MainActivity"
        const val COLUMNS = 4
    }
}
