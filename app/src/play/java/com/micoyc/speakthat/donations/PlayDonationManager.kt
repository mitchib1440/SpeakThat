package com.micoyc.speakthat.donations

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.google.android.material.button.MaterialButton
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.R
import java.lang.ref.WeakReference

/**
 * Play flavor donation manager backed by Google Play Billing.
 * Handles product lookup, purchase launch, consumption, and badge counting.
 */
class PlayDonationManager(private val appContext: Context) : DonationManager, PurchasesUpdatedListener {

    private val productIds = listOf(PRODUCT_TIP_SMALL, PRODUCT_TIP_MID, PRODUCT_TIP_LARGE)

    private val store = PlayDonationStore(appContext)

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        // Required for PBL 8+: explicitly opt in to pending purchases for one-time products.
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .setListener(this)
        .build()

    private var isConnected = false
    private var productDetails: Map<String, ProductDetails> = emptyMap()
    private var badgeUpdateCallback: ((Int) -> Unit)? = null
    private var currentActivityRef: WeakReference<Activity>? = null

    override fun showDonate(activity: Activity, fallback: () -> Unit) {
        currentActivityRef = WeakReference(activity)
        showPlayDialog(activity, fallback)
    }

    override fun getDonationBadgeCount(): Int = store.getBadgeCount()

    private fun ensureReady(activity: Activity, onReady: () -> Unit, onError: () -> Unit) {
        if (isConnected && productDetails.isNotEmpty()) {
            onReady()
            return
        }

        if (!isConnected) {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        isConnected = true
                        InAppLogger.logUserAction("Billing", "Play Billing connected")
                        queryProducts(onReady, onError)
                    } else {
                        InAppLogger.logError("Billing", "Setup failed: ${result.responseCode}")
                        onError()
                    }
                }

                override fun onBillingServiceDisconnected() {
                    isConnected = false
                    InAppLogger.logError("Billing", "Service disconnected")
                }
            })
        } else {
            queryProducts(onReady, onError)
        }
    }

    private fun queryProducts(onReady: () -> Unit, onError: () -> Unit) {
        val products = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(
            params,
            object : ProductDetailsResponseListener {
                override fun onProductDetailsResponse(
                    billingResult: BillingResult,
                    productDetailsResult: QueryProductDetailsResult
                ) {
                    val productDetailsList = productDetailsResult.productDetailsList ?: emptyList()
                    val size = productDetailsList.size
                    val code = billingResult.responseCode
                    val debugMessage = billingResult.debugMessage
                    val debugIds = productIds.joinToString(",")
                    InAppLogger.logUserAction(
                        "Billing",
                        "Query products response=$code, size=$size, msg=$debugMessage, requested=[$debugIds]"
                    )

                    if (code == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                        productDetails = productDetailsList.associateBy { it.productId }
                        InAppLogger.logUserAction("Billing", "Products loaded: ${productDetails.keys}")
                        onReady()
                    } else {
                        InAppLogger.logError("Billing", "Failed to load products: code=$code, size=$size")
                        onError()
                    }
                }
            }
        )
    }

    private fun showPlayDialog(activity: Activity, fallback: () -> Unit) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_donate_play, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .create()
        dialog.setOnDismissListener { badgeUpdateCallback = null }

        val badgeText = view.findViewById<TextView>(R.id.textBadgeCount)
        val badgePlaceholder = view.findViewById<TextView>(R.id.textBadgePlaceholder)
        badgeUpdateCallback = { count ->
            badgeText.text = activity.getString(R.string.play_donate_badge_count, count)
        }
        badgeUpdateCallback?.invoke(store.getBadgeCount())
        badgePlaceholder.text = activity.getString(R.string.play_donate_badge_placeholder)

        // Initial state: loading until products arrive
        setLoadingState(view, activity, isLoading = true)

        // Helper to safely update the dialog once products arrive (first open included).
        val onProductsReady: () -> Unit = {
            runOnUiThread {
                if (dialog.isShowing) {
                    populateProductsIntoView(view, activity)
                    setLoadingState(view, activity, isLoading = false)
                }
            }
        }

        val onProductsError: () -> Unit = {
            runOnUiThread {
                setLoadingState(view, activity, isLoading = false)
                if (dialog.isShowing) {
                    Toast.makeText(activity, activity.getString(R.string.play_donate_offline_fallback), Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    fallback()
                }
            }
        }

        view.findViewById<MaterialButton>(R.id.btnBuySmall).setOnClickListener {
            launchPurchase(activity, PRODUCT_TIP_SMALL)
        }

        view.findViewById<MaterialButton>(R.id.btnBuyMid).setOnClickListener {
            launchPurchase(activity, PRODUCT_TIP_MID)
        }

        view.findViewById<MaterialButton>(R.id.btnBuyLarge).setOnClickListener {
            launchPurchase(activity, PRODUCT_TIP_LARGE)
        }

        view.findViewById<MaterialButton>(R.id.btnMoreWays).setOnClickListener {
            openUrl(activity, activity.getString(R.string.play_donate_more_ways_url))
        }

        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        ensureReady(
            activity = activity,
            onReady = onProductsReady,
            onError = onProductsError
        )
    }

    private fun populateProductsIntoView(view: android.view.View, activity: Activity) {
        view.findViewById<TextView>(R.id.textPriceSmall)?.text = formattedPrice(PRODUCT_TIP_SMALL, activity)
        view.findViewById<TextView>(R.id.textPriceMid)?.text = formattedPrice(PRODUCT_TIP_MID, activity)
        view.findViewById<TextView>(R.id.textPriceLarge)?.text = formattedPrice(PRODUCT_TIP_LARGE, activity)
    }

    private fun setLoadingState(view: android.view.View, activity: Activity, isLoading: Boolean) {
        val loadingContainer = view.findViewById<android.view.View>(R.id.loadingContainer)
        val controls = listOf(R.id.btnBuySmall, R.id.btnBuyMid, R.id.btnBuyLarge)

        loadingContainer?.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE

        controls.forEach { id ->
            view.findViewById<android.view.View>(id)?.isEnabled = !isLoading
        }

        if (isLoading) {
            val loadingText = activity.getString(R.string.play_donate_loading)
            view.findViewById<TextView>(R.id.textPriceSmall)?.text = loadingText
            view.findViewById<TextView>(R.id.textPriceMid)?.text = loadingText
            view.findViewById<TextView>(R.id.textPriceLarge)?.text = loadingText
        }
    }

    private fun launchPurchase(activity: Activity, productId: String) {
        val details = productDetails[productId]
        if (details == null) {
            Toast.makeText(activity, R.string.play_donate_error, Toast.LENGTH_SHORT).show()
            InAppLogger.logError("Billing", "No product details for $productId")
            return
        }

        // Some billing client versions may not expose quantity; gracefully default to 1.
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()

        val billingParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, billingParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            InAppLogger.logError("Billing", "Launch failed: ${result.responseCode}")
        } else {
            InAppLogger.logUserAction("Billing", "Purchase launched for $productId (quantity chosen in Play UI)")
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { purchase ->
                handlePurchase(purchase)
            }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            InAppLogger.logUserAction("Billing", "Purchase cancelled")
        } else {
            InAppLogger.logError("Billing", "Purchase failed: ${result.responseCode}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val productId = purchase.products.firstOrNull()
                val weight = PRODUCT_WEIGHTS[productId] ?: 1
                val increment = weight * purchase.quantity
                store.incrementBadgeCount(increment)
                val newCount = store.getBadgeCount()
                InAppLogger.logUserAction("Billing", "Donation consumed: product=$productId, qty=${purchase.quantity}, weight=$weight, increment=$increment, total=$newCount")
                runOnUiThread {
                    badgeUpdateCallback?.invoke(newCount)
                    currentActivityRef?.get()?.let { activity ->
                        Toast.makeText(activity, activity.getString(R.string.play_donate_thank_you, newCount), Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                InAppLogger.logError("Billing", "Consume failed: ${billingResult.responseCode}")
            }
        }
    }

    private fun formattedPrice(productId: String, activity: Activity): String {
        val details = productDetails[productId]
        val offer = details?.oneTimePurchaseOfferDetails
        return offer?.formattedPrice ?: activity.getString(R.string.play_donate_price_unavailable)
    }

    private fun openUrl(activity: Activity, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            activity.startActivity(intent)
            InAppLogger.logUserAction("Donate", "Opened support URL: $url")
        } catch (e: Exception) {
            Toast.makeText(activity, activity.getString(R.string.play_donate_error), Toast.LENGTH_SHORT).show()
            InAppLogger.logError("Donate", "Failed to open URL: ${e.message}")
        }
    }

    private fun runOnUiThread(block: () -> Unit) {
        currentActivityRef?.get()?.runOnUiThread { block() }
    }

    private companion object {
        const val PRODUCT_TIP_SMALL = "donation_tip_low"
        const val PRODUCT_TIP_MID = "donation_tip"
        const val PRODUCT_TIP_LARGE = "donation_tip_large"

        val PRODUCT_WEIGHTS = mapOf(
            PRODUCT_TIP_SMALL to 1,
            PRODUCT_TIP_MID to 7,
            PRODUCT_TIP_LARGE to 12
        )
    }
}

private class PlayDonationStore(context: Context) {
    private val prefs = context.getSharedPreferences("play_donations", Context.MODE_PRIVATE)

    fun getBadgeCount(): Int = prefs.getInt(KEY_BADGE_COUNT, 0)

    fun incrementBadgeCount(amount: Int) {
        val newCount = getBadgeCount() + amount
        prefs.edit().putInt(KEY_BADGE_COUNT, newCount).apply()
    }

    private companion object {
        const val KEY_BADGE_COUNT = "badge_count"
    }
}

