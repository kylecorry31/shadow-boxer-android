package com.kylecorry.kravtrainer.ui

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.children
import androidx.fragment.app.Fragment
import com.kylecorry.kravtrainer.R
import com.kylecorry.kravtrainer.doTransaction
import com.kylecorry.kravtrainer.domain.models.*
import com.kylecorry.kravtrainer.domain.services.PunchClassifier
import com.kylecorry.kravtrainer.domain.services.PunchComboTracker
import com.kylecorry.kravtrainer.domain.services.PunchStatAggregator
import com.kylecorry.kravtrainer.infrastructure.BluetoothGloves
import com.kylecorry.kravtrainer.infrastructure.TrainingTimer
import java.util.*
import kotlin.concurrent.timer
import kotlin.random.Random


class TrainingFragment(private val time: Int?) : Fragment(), TextToSpeech.OnInitListener, Observer {

    private lateinit var timeProgressBar: ProgressBar
    private lateinit var comboTxt: TextView
    private lateinit var endBtn: Button
    private lateinit var comboProgressDots: LinearLayout
    private lateinit var currentMoveTxt: TextView

    private lateinit var comboTracker: PunchComboTracker
    private lateinit var tts: TextToSpeech
    private lateinit var gloves: BluetoothGloves
    private var timer: TrainingTimer? = null

    private val leftPunchClassifier = PunchClassifier()
    private val rightPunchClassifier = PunchClassifier()
    private var punchStatAggregator = PunchStatAggregator()

    private var lastLeftPunch: PunchType? = null
    private var lastRightPunch: PunchType? = null

    private var mediaPlayer: MediaPlayer? = null

    private lateinit var handler: Handler

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_training, container, false)

        timeProgressBar = view.findViewById(R.id.time_progress)
        comboTxt = view.findViewById(R.id.current_combo)
        endBtn = view.findViewById(R.id.end_session_btn)
        comboProgressDots = view.findViewById(R.id.combo_progress_dots)
        currentMoveTxt = view.findViewById(R.id.current_move)

        endBtn.setOnClickListener { returnToTrainingSelect() }

        gloves = BluetoothGloves()

        if (time == null){
            timeProgressBar.visibility = View.INVISIBLE
        } else {
            timeProgressBar.max = time
            timeProgressBar.progress = time
            timer = TrainingTimer(time)
        }

        return view
    }

    private fun returnToTrainingSelect(){
        fragmentManager?.doTransaction {
            this.replace(
                R.id.fragment_holder,
                TrainingSelectFragment()
            )
        }
    }

    override fun onResume() {
        super.onResume()
        tts = TextToSpeech(context, this)

        gloves.start()
        timer?.start()

        gloves.addObserver(this)
        timer?.addObserver(this)

        handler = Handler(Looper.getMainLooper())
    }

    override fun onPause() {
        super.onPause()

        timer?.deleteObserver(this)
        gloves.deleteObserver(this)

        timer?.stop()
        if (tts.isSpeaking){
            tts.stop()
        }
        tts.shutdown()
        gloves.stop()

        mediaPlayer?.release()

    }

    private fun onPunch(punch: Punch){

        println(punch)

        if (comboTracker.matches(punch)){
            punchStatAggregator.correct(punch)
            playSound(R.raw.success)
            comboTracker.next()
            updateComboProgress()
        } else {
            punchStatAggregator.incorrect(punch)
            playSound(R.raw.fail)
        }

        if (comboTracker.isDone){
            timer(period = 1000){
                handler.post {
                    nextCombo()
                }
                cancel()
            }
        }
    }

    private fun nextCombo(){
        val combo = getNextCombo()
        comboTracker = PunchComboTracker(combo)
        announceNewCombo()
    }

    private fun getNextCombo(): PunchCombo {
        val idx = Random.nextInt(PunchCombos.combos.size)
        return PunchCombos.combos[idx]
    }

    private fun announceNewCombo(){
        tts.speak(comboTracker.combo.name, TextToSpeech.QUEUE_FLUSH, null)
        comboTxt.text = comboTracker.combo.name
        updateComboProgress()
    }

    private fun updateComboProgress(){
        var idx = 0
        for (child in comboProgressDots.children){
            if (idx < comboTracker.combo.punches.size) {
                child.visibility = View.VISIBLE
            } else {
                child.visibility = View.GONE
            }
            if (idx < comboTracker.index){
                (child as ImageView).setImageResource(R.drawable.ic_complete)
            } else {
                (child as ImageView).setImageResource(R.drawable.ic_not_complete)
            }
            idx++
        }

        val currentMove = comboTracker.currentPunch
        currentMoveTxt.text = "${currentMove?.hand} ${currentMove?.punchType}"
    }

    private fun playSound(id: Int){
        mediaPlayer = MediaPlayer.create(context, id)
        mediaPlayer?.setVolume(0.1f, 0.1f)
        mediaPlayer?.start()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS){
            nextCombo()
        }
    }

    fun onGlovesUpdate(){
        if (!gloves.isConnected){
            // TODO: Alert user
            Toast.makeText(context, "Gloves disconnected", Toast.LENGTH_LONG).show()
            return
        }

        // TODO: Set connection indicator

        val leftPunchType = leftPunchClassifier.classify(gloves.left)
        val rightPunchType = rightPunchClassifier.classify(gloves.right)

        if (leftPunchType != null && leftPunchType != lastLeftPunch){
            onPunch(Punch.left(leftPunchType))
        }

        if (rightPunchType != null && rightPunchType != lastRightPunch){
            onPunch(Punch.right(rightPunchType))
        }

        lastLeftPunch = leftPunchType
        lastRightPunch = rightPunchType

    }

    fun onTimerUpdate(){
        val secondsLeft = timer?.remainingSeconds ?: 0

        if (secondsLeft <= 0) {
            // Announce end
            // Return to training select
            returnToTrainingSelect()
        }

        timeProgressBar.progress = secondsLeft
    }

    override fun update(o: Observable?, arg: Any?) {
        if (o == gloves) onGlovesUpdate()
        if (o == timer) onTimerUpdate()
    }
}
