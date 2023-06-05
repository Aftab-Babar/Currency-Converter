package com.aftab.currencies.repository

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.awaitResult
import com.github.kittinunf.fuel.moshi.moshiDeserializerOf
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.map
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.aftab.currencies.model.ApiProvider
import com.aftab.currencies.model.Currency
import com.aftab.currencies.model.ExchangeRates
import com.aftab.currencies.model.Timeline
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

object ExchangeRatesService {

    /**
     * Get all the current exchange rates from the given api provider. Base will be Euro.
     */
    suspend fun getRates(apiProvider: com.aftab.currencies.model.ApiProvider, date: LocalDate? = null): Result<ExchangeRates, FuelError> {
        // Currency conversions are done relatively to each other - so it basically doesn't matter
        // which base is used here. However, Euro is a strong currency, preventing rounding errors.
        val base = Currency.EUR
        val dateString = if (date != null) date.format(DateTimeFormatter.ISO_LOCAL_DATE) else "latest"

        return Fuel.get(
            when (apiProvider) {
                com.aftab.currencies.model.ApiProvider.EXCHANGERATE_HOST -> apiProvider.baseUrl +
                        "/$dateString" +
                        "?base=$base" +
                        "&v=${UUID.randomUUID()}"
                com.aftab.currencies.model.ApiProvider.FRANKFURTER_APP -> apiProvider.baseUrl +
                        "/$dateString" +
                        "?base=$base"
                com.aftab.currencies.model.ApiProvider.FER_EE -> apiProvider.baseUrl +
                        "/$dateString" +
                        "?base=$base"
                com.aftab.currencies.model.ApiProvider.INFOR_EURO -> apiProvider.baseUrl +
                        "/monthly-rates" +
                        if (date != null) "?year=${date.year}" + "&month=${date.monthValue}"
                        else ""
            }
        ).awaitResult(
            moshiDeserializerOf(
                Moshi.Builder()
                    .addLast(KotlinJsonAdapterFactory())
                    .apply {
                        if (apiProvider == com.aftab.currencies.model.ApiProvider.INFOR_EURO) {
                            add(InforEuroAdapter(date ?: LocalDate.now(ZoneOffset.UTC)))
                        } else {
                            add(RatesAdapter(base))
                            add(LocalDateAdapter())
                        }
                    }
                    .build()
                    .adapter(ExchangeRates::class.java)
            )
        ).map { rates ->
            rates.copy(provider = apiProvider)
        }
    }

    /**
     * Get the historic rates of the past year between the given base and symbol.
     * Won't get all the symbols, as it makes a big difference in transferred data size:
     * ~12 KB for one symbol to ~840 KB for all symbols
     */
    suspend fun getTimeline(apiProvider: com.aftab.currencies.model.ApiProvider, base: Currency, symbol: Currency): Result<Timeline, FuelError> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusYears(1)

        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        // can't search for FOK - have to use DKK instead
        val parameterBase = if (base == Currency.FOK) "DKK" else base.iso4217Alpha()
        val parameterSymbol = if (symbol == Currency.FOK) "DKK" else symbol.iso4217Alpha()
        // call api
        return Fuel.get(
            when (apiProvider) {
                com.aftab.currencies.model.ApiProvider.EXCHANGERATE_HOST -> "${apiProvider.baseUrl}/timeseries" +
                        "?base=$parameterBase" +
                        "&v=${UUID.randomUUID()}" +
                        "&start_date=${startDate.format(dateFormatter)}" +
                        "&end_date=${endDate.format(dateFormatter)}" +
                        "&symbols=$parameterSymbol"
                com.aftab.currencies.model.ApiProvider.FRANKFURTER_APP -> "${apiProvider.baseUrl}/" +
                        startDate.format(dateFormatter) +
                        ".." +
                        endDate.format(dateFormatter) +
                        "?base=$parameterBase" +
                        "&symbols=$parameterSymbol"
                com.aftab.currencies.model.ApiProvider.FER_EE -> "${apiProvider.baseUrl}/" +
                        startDate.format(dateFormatter) +
                        ".." +
                        endDate.format(dateFormatter) +
                        "?base=$parameterBase" +
                        "&symbols=$parameterSymbol"
                com.aftab.currencies.model.ApiProvider.INFOR_EURO -> "${apiProvider.baseUrl}/" +
                        "currencies/" +
                        parameterSymbol
            }
        ).awaitResult(
            moshiDeserializerOf(
                Moshi.Builder()
                    .addLast(KotlinJsonAdapterFactory())
                    .apply {
                        if (apiProvider == com.aftab.currencies.model.ApiProvider.INFOR_EURO) {
                            add(InforEuroTimelineAdapter(startDate, endDate))
                        } else {
                            add(RatesAdapter(base))
                            add(LocalDateAdapter())
                            add(TimelineRatesAdapter(symbol))
                        }
                    }
                    .build()
                    .adapter(Timeline::class.java)
            )
        ).map { timeline ->
            when (base) {
                // change dkk base back to fok, if needed
                Currency.FOK -> timeline.copy(base = base.iso4217Alpha())
                else -> timeline
            }
        }.map { timeline ->
            timeline.copy(provider = apiProvider)
        }
    }

}
