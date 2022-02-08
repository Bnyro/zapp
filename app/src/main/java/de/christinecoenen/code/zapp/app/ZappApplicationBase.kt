package de.christinecoenen.code.zapp.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import de.christinecoenen.code.zapp.R
import de.christinecoenen.code.zapp.app.settings.repository.SettingsRepository
import de.christinecoenen.code.zapp.repositories.ChannelRepository
import de.christinecoenen.code.zapp.repositories.MediathekRepository
import de.christinecoenen.code.zapp.utils.system.NotificationHelper.createBackgroundPlaybackChannel
import org.acra.ACRA
import org.acra.ACRA.init
import org.acra.BuildConfig
import org.acra.ReportField
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.DialogConfigurationBuilder
import org.acra.config.MailSenderConfigurationBuilder
import org.acra.data.StringFormat
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

abstract class ZappApplicationBase : Application() {

	val channelRepository: ChannelRepository
		get() = koin.get()

	val mediathekRepository: MediathekRepository
		get() = koin.get()

	private lateinit var koin: Koin

	fun reportError(throwable: Throwable?) {
		if (ACRA.isInitialised) {
			ACRA.errorReporter.handleException(throwable)
		}

		Timber.e(throwable)
	}

	override fun onCreate() {
		super.onCreate()

		setUpLogging()
		createBackgroundPlaybackChannel(this)

		koin = startKoin {
			androidLogger(Level.ERROR)
			androidContext(this@ZappApplicationBase)
			modules(KoinModules.AppModule)
		}.koin

		val settingsRepository = SettingsRepository(this)
		AppCompatDelegate.setDefaultNightMode(settingsRepository.uiMode)
	}

	override fun attachBaseContext(base: Context?) {
		super.attachBaseContext(base)

		if (Thread.getDefaultUncaughtExceptionHandler() != null) {
			// exclude test environments
			setUpCrashReporting()
		}
	}

	protected abstract fun setUpLogging()

	private fun setUpCrashReporting() {
		val builder = CoreConfigurationBuilder(this)
			.apply {
				buildConfigClass = BuildConfig::class.java
				reportFormat = StringFormat.KEY_VALUE_LIST
				reportContent = arrayOf(
					ReportField.REPORT_ID,
					ReportField.USER_EMAIL,
					ReportField.USER_COMMENT,
					ReportField.IS_SILENT,
					ReportField.USER_CRASH_DATE,
					ReportField.APP_VERSION_NAME,
					ReportField.APP_VERSION_CODE,
					ReportField.ANDROID_VERSION,
					ReportField.PHONE_MODEL,
					ReportField.BRAND,
					ReportField.SHARED_PREFERENCES,
					ReportField.STACK_TRACE
				)
				excludeMatchingSharedPreferencesKeys = arrayOf(
					"default.acra.legacyAlreadyConvertedToJson",
					"default.acra.lastVersionNr",
					"default.acra.legacyAlreadyConvertedTo4.8.0"
				)

				getPluginConfigurationBuilder(DialogConfigurationBuilder::class.java)
					.withResText(R.string.error_app_crash)
					.withResTitle(R.string.app_name)
					.withResIcon(R.drawable.ic_sad_tv)
					.withResPositiveButtonText(R.string.action_continue)
					.withResTheme(R.style.ChrashDialog)
					.withEnabled(true)


				getPluginConfigurationBuilder(MailSenderConfigurationBuilder::class.java)
					.withMailTo("Zapp Entwicklung <code.coenen@gmail.com>")
					.withResSubject(R.string.error_app_crash_mail_subject)
					.withResBody(R.string.error_app_crash_mail_body)
					.withReportAsFile(false)
					.withEnabled(true)
			}

		init(this, builder)
	}

}
