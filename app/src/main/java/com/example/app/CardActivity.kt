package com.example.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.stripe.android.PaymentConfiguration
import com.stripe.android.model.ConfirmPaymentIntentParams
import com.stripe.android.payments.paymentlauncher.PaymentLauncher
import com.stripe.android.payments.paymentlauncher.PaymentResult
import kotlinx.android.synthetic.main.card_activity.*
import kotlinx.coroutines.launch

class CardActivity : AppCompatActivity() {

    private lateinit var paymentIntentClientSecret: String
    private lateinit var paymentLauncher: PaymentLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.card_activity)

        // Initialize Stripe SDK with your publishable key securely from BuildConfig
        PaymentConfiguration.init(
            applicationContext,
            BuildConfig.STRIPE_PUBLISHABLE_KEY
        )

        val paymentConfiguration = PaymentConfiguration.getInstance(applicationContext)

        paymentLauncher = PaymentLauncher.Companion.create(
            this,
            paymentConfiguration.publishableKey,
            paymentConfiguration.stripeAccountId,
            ::onPaymentResult
        )

        startCheckout()
    }

    private fun toggleLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        payButton.isEnabled = !isLoading
    }

    private fun displayAlert(
        title: String,
        message: String,
        restartDemo: Boolean = false
    ) {
        runOnUiThread {
            val builder = AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
            if (restartDemo) {
                builder.setPositiveButton("Restart demo") { _, _ ->
                    cardInputWidget.clear()
                    startCheckout()
                }
            } else {
                builder.setPositiveButton("Ok", null)
            }
            builder.create().show()
        }
    }

    private fun startCheckout() {
        toggleLoading(true)

        ApiClient().createPaymentIntent("card") { clientSecret, error ->
            toggleLoading(false)

            clientSecret?.let {
                println("🧾 Client Secret from Server: $it")
                paymentIntentClientSecret = it
            }

            error?.let {
                displayAlert("Failed to load page", "Error: $error")
            }
        }

        payButton.setOnClickListener {
            cardInputWidget.paymentMethodCreateParams?.let { params ->
                BackgroundTaskTracker.onStart()
                val confirmParams = ConfirmPaymentIntentParams
                    .createWithPaymentMethodCreateParams(params, paymentIntentClientSecret)
                toggleLoading(true)
                lifecycleScope.launch {
                    paymentLauncher.confirm(confirmParams)
                }
            }
        }
    }

    private fun onPaymentResult(paymentResult: PaymentResult) {
        val message = when (paymentResult) {
            is PaymentResult.Completed -> "Completed!"
            is PaymentResult.Canceled -> "Canceled!"
            is PaymentResult.Failed -> "Failed: ${paymentResult.throwable.message}"
        }
        displayAlert("Payment Result:", message, restartDemo = true)
        toggleLoading(false)
        BackgroundTaskTracker.onStop()
    }
}
