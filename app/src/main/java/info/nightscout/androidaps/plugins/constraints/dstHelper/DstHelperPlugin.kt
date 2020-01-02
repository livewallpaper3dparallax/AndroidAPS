package info.nightscout.androidaps.plugins.constraints.dstHelper

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.ConstraintsInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationWithAction
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DstHelperPlugin @Inject constructor(): PluginBase(PluginDescription()
    .mainType(PluginType.CONSTRAINTS)
    .neverVisible(true)
    .alwaysEnabled(true)
    .showInList(false)
    .pluginName(R.string.dst_plugin_name)
), ConstraintsInterface {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var mainApp: MainApp
    @Inject lateinit var sp : SP
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Inject lateinit var loopPlugin: LoopPlugin

    private val DISABLE_TIMEFRAME_HOURS = -3
    private val WARN_PRIOR_TIMEFRAME_HOURS = 12

    //Return false if time to DST change happened in the last 3 hours.
    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        val pump = configBuilderPlugin.activePump
        if (pump == null || pump.canHandleDST()) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Pump can handle DST")
            return value
        }
        val cal = Calendar.getInstance()
        if (willBeDST(cal)) {
            val snoozedTo: Long = sp.getLong(R.string.key_snooze_dst_in24h, 0L)
            if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                val notification = NotificationWithAction(mainApp, Notification.DST_IN_24H, resourceHelper.gs(R.string.dst_in_24h_warning), Notification.LOW)
                notification.action(R.string.snooze, Runnable {
                    sp.putLong(R.string.key_snooze_dst_in24h, System.currentTimeMillis() + T.hours(24).msecs())
                })
                rxBus.send(EventNewNotification(notification))
            }
        }
        if (!value.value()) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Already not allowed - don't check further")
            return value
        }
        if (wasDST(cal)) {
            if (!loopPlugin.isSuspended) {
                val snoozedTo: Long = sp.getLong(R.string.key_snooze_loopdisabled, 0L)
                if (snoozedTo == 0L || System.currentTimeMillis() > snoozedTo) {
                    val notification = NotificationWithAction(mainApp, Notification.DST_LOOP_DISABLED, resourceHelper.gs(R.string.dst_loop_disabled_warning), Notification.LOW)
                    notification.action(R.string.snooze, Runnable {
                        sp.putLong(R.string.key_snooze_loopdisabled, System.currentTimeMillis() + T.hours(24).msecs())
                    })
                    rxBus.send(EventNewNotification(notification))
                }
            } else {
                aapsLogger.debug(LTag.CONSTRAINTS, "Loop already suspended")
            }
            value.set(false, "DST in last 3 hours.", this)
        }
        return value
    }

    fun wasDST(now: Calendar): Boolean {
        val ago = now.clone() as Calendar
        ago.add(Calendar.HOUR, DISABLE_TIMEFRAME_HOURS)
        return now[Calendar.DST_OFFSET] != ago[Calendar.DST_OFFSET]
    }

    fun willBeDST(now: Calendar): Boolean {
        val ago = now.clone() as Calendar
        ago.add(Calendar.HOUR, WARN_PRIOR_TIMEFRAME_HOURS)
        return now[Calendar.DST_OFFSET] != ago[Calendar.DST_OFFSET]
    }
}