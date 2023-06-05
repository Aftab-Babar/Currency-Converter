package com.aftab.currencies.util

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.lifecycle.LiveData
import com.aftab.currencies.model.ApiProvider
import com.aftab.currencies.model.Currency
import com.aftab.currencies.model.ExchangeRates
import com.aftab.currencies.model.Rate
import java.time.LocalDate

class SharedPreferenceExchangeRatesLiveData(private val sharedPrefs: SharedPreferences) : LiveData<ExchangeRates?>() {

    private fun getValueFromPreferences(): ExchangeRates? {
        return if (sharedPrefs.getString("_base", null) == null || sharedPrefs.getString("_date", null) == null)
            null
        else
            ExchangeRates(
                true, // success always true, when serving cached data
                null, // error message always null, when serving cached data
                Currency.fromString(sharedPrefs.getString("_base", null)!!),
                LocalDate.parse(sharedPrefs.getString("_date", null))!!,
                sharedPrefs.all.entries
                    .filter { !it.key.startsWith("_") }
                    .sortedBy { it.key }
                    .mapNotNull { Currency.fromString(it.key!!)?.let { currency -> Rate(currency, (it.value as Float)) } }
                    .toList(),
                sharedPrefs.getInt("_provider", -1).let { com.aftab.currencies.model.ApiProvider.fromNumber(it) }
            )
    }

    private val preferenceChangeListener = OnSharedPreferenceChangeListener { _: SharedPreferences?, _: String? ->
            postValue(getValueFromPreferences())
    }

    override fun onActive() {
        super.onActive()
        postValue(getValueFromPreferences())
        sharedPrefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onInactive() {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onInactive()
    }

}
