package com.superwaking

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager

/** 用 USAGE_ALARM 播放系统闹铃，独立于铃声/通知/媒体流。 */
object SoundAlert {

    private var player: MediaPlayer? = null

    fun raiseAlarmVolume(context: Context) {
        try {
            val am = context.getSystemService(AudioManager::class.java)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) < max) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
                Diagnostics.log(context, "alarm_volume_raised to $max")
            }
        } catch (e: Exception) {
            // DND/固定音量策略下可能被拒，仍然尝试播放
            Diagnostics.log(context, "alarm_volume_denied ${e.javaClass.simpleName}")
        }
    }

    fun play(context: Context) {
        stop()
        raiseAlarmVolume(context)

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        if (uri == null) {
            Diagnostics.log(context, "sound_no_uri")
            return
        }

        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            p.setDataSource(context, uri)
            p.isLooping = true
            p.setOnErrorListener { _, what, extra ->
                Diagnostics.log(context, "sound_error $what/$extra")
                true
            }
            p.prepare()
            p.start()
            player = p
            Diagnostics.log(context, "sound_start ${uri.lastPathSegment}")
        } catch (e: Exception) {
            Diagnostics.log(context, "sound_failed ${e.javaClass.simpleName}")
        }
    }

    fun stop() {
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        }
        player = null
    }

    fun isPlaying(): Boolean = try {
        player?.isPlaying == true
    } catch (_: Exception) {
        false
    }

    fun alarmVolume(context: Context): Pair<Int, Int> {
        val am = context.getSystemService(AudioManager::class.java)
        return am.getStreamVolume(AudioManager.STREAM_ALARM) to
            am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    }
}
