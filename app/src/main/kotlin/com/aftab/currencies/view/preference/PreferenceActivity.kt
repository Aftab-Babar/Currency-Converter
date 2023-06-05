package com.aftab.currencies.view.preference

import android.os.Bundle
import android.view.MenuItem
import com.aftab.currencies.R
import com.aftab.currencies.view.BaseActivity

class PreferenceActivity: BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_preference)

        // title bar
        setTitle(R.string.title_preferences)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> false
        }
    }
}
