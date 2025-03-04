package net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.meeting.main

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import com.prolificinteractive.materialcalendarview.*
import kotlinx.android.synthetic.main.content_meeting.*
import net.muliba.changeskin.FancySkinManager
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.O2
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.O2SDKManager
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.R
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.base.BaseMVPViewPagerFragment
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.meeting.apply.MeetingApplyActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.meeting.invited.MeetingDetailInfoActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.webview.TaskWebViewActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.component.adapter.CommonRecycleViewAdapter
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.component.adapter.CommonRecyclerViewHolder
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.main.identity.ProcessWOIdentityJson
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.main.process.ProcessDataJson
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.meeting.MeetingInfoJson
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.DateHelper
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.XLog
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.XToast
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.decorator.EventDecorator
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.decorator.SelectorDecorator
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.decorator.TodayDecorator
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.*
import org.threeten.bp.DayOfWeek
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneId
import java.util.*

/**
 * Created by jamlee on 2017/8/14 0014.
 */
class MeetingMainFragment : BaseMVPViewPagerFragment<MeetingMainFragmentContract.View, MeetingMainFragmentContract.Presenter>(),
        MeetingMainFragmentContract.View, OnDateSelectedListener, OnMonthChangedListener, IdentifyChooseDialog.DialogCallback {

    private val meetingList = ArrayList<MeetingInfoJson>()
    private val selectorDecorator: SelectorDecorator by lazy {
        SelectorDecorator(
            activity
        )
    }
    private lateinit var daySelected: String
    private lateinit var monthSelected: String
    private var identifyDialog: IdentifyChooseDialog? = null
    private var meetingConfig: ProcessDataJson? = null
    override var mPresenter: MeetingMainFragmentContract.Presenter = MeetingMainFragmentPresenter()
    private var isWeekView = true

    override fun layoutResId(): Int = R.layout.content_meeting

    override fun initUI() {
        val today = DateHelper.nowByFormate("yyyy-MM-dd")
        tv_meeting_choose_day.text = today.substring(0, 10)
        daySelected = today
        monthSelected = today

        calendarView_meeting_date.setOnDateChangedListener(this)
        calendarView_meeting_date.setOnMonthChangedListener(this)
        calendarView_meeting_date.setWeekDayLabels(R.array.custom_weekdays)
        calendarView_meeting_date.showOtherDates = MaterialCalendarView.SHOW_DEFAULTS
        calendarView_meeting_date.selectedDate =  CalendarDay.from( LocalDate.now())
        calendarView_meeting_date.addDecorators(
            TodayDecorator(
                activity
            ), selectorDecorator)
        calendarView_meeting_date.setHeaderTextAppearance(R.style.TextAppearance_MaterialCalendarWidget_Header)
        calendarView_meeting_date.setDateTextAppearance(R.style.TextAppearance_MaterialCalendarWidget_Date)
        calendarView_meeting_date.setWeekDayTextAppearance(R.style.TextAppearance_MaterialCalendarWidget_WeekDay)
        calendarView_meeting_date.topbarVisible = false
        calendarView_meeting_date.state().edit()
            .setFirstDayOfWeek(DayOfWeek.SUNDAY)
            .commit()

        meeting_recycler_view.layoutManager = LinearLayoutManager(activity)
        meeting_recycler_view.adapter = adapter
        adapter.setOnItemClickListener { _, position ->
            val meetingInvited = meetingList[position]
            mPresenter.getMeetingById(meetingInvited.id)
        }

        ll_meeting_main_month_chang.setOnClickListener {
            isWeekView = !isWeekView
            if (isWeekView) {
                calendarView_meeting_date.state().edit()
                        .setCalendarDisplayMode(CalendarMode.WEEKS)
                        .commit()
                image_meeting_main_month_chang.setImageResource(R.mipmap.icon_arrow_down)
            } else {
                calendarView_meeting_date.state().edit()
                        .setCalendarDisplayMode(CalendarMode.MONTHS)
                        .commit()
                image_meeting_main_month_chang.setImageResource(R.mipmap.icon_arrow_up)
            }

        }

    }

    override fun lazyLoad() {
        showLoadingDialog()
        if (TextUtils.isEmpty((activity as MeetingMainActivity).meetingConfig)) {
            mPresenter.getMeetingConfig()
        } else {
            loadMonthMeetings()
            loadDayMeetings()
        }
    }

    override fun findMyMeetingByDay(list: List<MeetingInfoJson>) {
        if (list.isEmpty()) {
            meeting_recycler_view.visibility = View.GONE
            ll_empty_meeting.visibility = View.VISIBLE
        } else {
            ll_empty_meeting.visibility = View.GONE
            meeting_recycler_view.visibility = View.VISIBLE
            meetingList.clear()
            meetingList.addAll(list)
        }
        adapter.notifyDataSetChanged()
        hideLoadingDialog()
    }

    override fun findMyMeetingByMonth(list: List<MeetingInfoJson>) {
        val meetingDays = ArrayList<CalendarDay>()
        list.map {
//            val c = DateHelper.gc(it.startTime, "yyyy-MM-dd HH:mm:ss")
            val date = DateHelper.convertStringToDate("yyyy-MM-dd HH:mm:ss", it.startTime)
            val localDate = CalendarDay.from(Instant.ofEpochMilli(date.time).atZone(ZoneId.systemDefault()).toLocalDate())
            meetingDays.add(localDate)
        }
        calendarView_meeting_date.removeDecorators()
        calendarView_meeting_date.addDecorators(
            TodayDecorator(
                activity
            ),
                selectorDecorator
        )
        calendarView_meeting_date.addDecorator(
            EventDecorator(
                FancySkinManager.instance().getColor(activity!!, R.color.z_color_primary_dark),
                meetingDays
            )
        )
        hideLoadingDialog()
    }

    override fun onDateSelected(widget: MaterialCalendarView, date: CalendarDay, selected: Boolean) {
        selectorDecorator.setDate(date)
        val monthString = if (date.month > 9) {
            "${date.month}"
        }else {
            "0${date.month}"
        }
        val dateString = if (date.day > 9) {
            "${date.day}"
        }else {
            "0${date.day}"
        }
        val str = "${date.year}-$monthString-$dateString"
        widget.invalidateDecorators()
        daySelected = str
        tv_meeting_choose_day.text = str
        showLoadingDialog()
        loadDayMeetings()
    }

    override fun onMonthChanged(widget: MaterialCalendarView?, date: CalendarDay?) {
        if (date != null) {
            val monthString = if (date.month > 9) {
                "${date.month}"
            }else {
                "0${date.month}"
            }
            val dateString = if (date.day > 9) {
                "${date.day}"
            }else {
                "0${date.day}"
            }
            val str = "${date.year}-$monthString-$dateString"
            XLog.info("短短的$str")
            val newMonth = str.substring(0, 7)
            val oldmonth = monthSelected.substring(0, 7)
            if (newMonth != oldmonth) {
                monthSelected = str
                showLoadingDialog()
                loadMonthMeetings()
            }
        }
    }

    override fun setMeetingConfig(config: String) {
        XLog.info("config: $config")
        if (activity == null) {
            return
        }
        (activity as MeetingMainActivity).meetingConfig = config
        O2SDKManager.instance().prefs().edit {
            putString(O2.PRE_MEETING_CONFIG_KEY, config)
        }
        if (TextUtils.isEmpty(config)) {
            fab_meeting_create.gone()
        } else {
            meetingConfig = O2SDKManager.instance().gson.fromJson<ProcessDataJson>(config, ProcessDataJson::class.java)
            if (meetingConfig?.mobileCreateEnable == true) {
                fab_meeting_create.visible()
                fab_meeting_create.setOnClickListener { applyMeeting() }
            } else {
                fab_meeting_create.gone()
            }
        }
        loadMonthMeetings()
        loadDayMeetings()
    }

    override fun loadCurrentPersonIdentity(list: List<ProcessWOIdentityJson>) {
        if (list.size == 1) {
            val identifyId = list[0].distinguishedName
            val processId = meetingConfig?.process?.id
            if (!TextUtils.isEmpty(processId)) {
                mPresenter.startProcess("", identifyId, processId!!)
            }else {
                hideLoadingDialog()
                XToast.toastShort(context, "流程id缺失")
            }

        } else {
            hideLoadingDialog()
            identifyDialog = IdentifyChooseDialog(activity!!, list, this)
            identifyDialog?.show()
        }
    }

    override fun positiveCallback(identifyId: String) {
        identifyDialog?.dismiss()
        val processId = meetingConfig?.process?.id
        if (!TextUtils.isEmpty(processId)) {
            showLoadingDialog()
            mPresenter.startProcess("", identifyId, processId!!)
        }else {
            XToast.toastShort(context, "流程id缺失")
        }
    }

    override fun negativeCallback() {
        identifyDialog?.dismiss()
    }

    override fun startProcessSuccess(workId: String) {
        hideLoadingDialog()
        val bundle = Bundle()
        bundle.putString(TaskWebViewActivity.WORK_WEB_VIEW_WORK, workId)
        bundle.putString(TaskWebViewActivity.WORK_WEB_VIEW_TITLE, "拟稿")
        activity?.go<TaskWebViewActivity>(bundle)
    }

    override fun startProcessFail(message: String) {
        XToast.toastShort(activity, "启动流程失败, $message")
        hideLoadingDialog()
    }

    private fun applyMeeting() {
        val processId = meetingConfig?.process?.id
        if (TextUtils.isEmpty(processId)) {
            activity?.go<MeetingApplyActivity>()
        }else {
            showLoadingDialog()
            mPresenter.loadCurrentPersonIdentityWithProcess(processId!!)
        }
    }

    private fun loadDayMeetings() {
        if ((activity as MeetingMainActivity).isCheckViewer) {
            val year = daySelected.substring(0, 4)
            val month = daySelected.substring(5, 7)
            val day = daySelected.substring(8, 10)
            mPresenter.findMyMeetingByDay(year, month, day, (activity as MeetingMainActivity).isViewer)
        } else {
            mPresenter.checkViewer(true, (activity as MeetingMainActivity).meetingConfig)
        }
    }

    private fun loadMonthMeetings() {
        if ((activity as MeetingMainActivity).isCheckViewer) {
            mPresenter.findMyMeetingByMonth(monthSelected, isWeekView, (activity as MeetingMainActivity).isViewer)
        } else {
            mPresenter.checkViewer(false, (activity as MeetingMainActivity).meetingConfig)
        }
    }

    override fun checkViewerBack(isDay: Boolean, result: Boolean) {
        (activity as MeetingMainActivity).isViewer = result
        (activity as MeetingMainActivity).isCheckViewer = true
        if (isDay) {
            val year = daySelected.substring(0, 4)
            val month = daySelected.substring(5, 7)
            val day = daySelected.substring(8, 10)
            mPresenter.findMyMeetingByDay(year, month, day, (activity as MeetingMainActivity).isViewer)
        } else {
            mPresenter.findMyMeetingByMonth(monthSelected, isWeekView, (activity as MeetingMainActivity).isViewer)
        }
    }

    override fun onException(message: String) {
        hideLoadingDialog()
        XToast.toastShort(activity, message)
    }

    override fun getMeetingById(meetingInfo: MeetingInfoJson) {
        activity?.let {
            MeetingDetailInfoActivity.openMeetingDetail(it, meetingInfo)
        }
    }


    private val adapter: CommonRecycleViewAdapter<MeetingInfoJson> by lazy {
        object : CommonRecycleViewAdapter<MeetingInfoJson>(activity, meetingList, R.layout.item_meeting_list_view) {
            override fun convert(holder: CommonRecyclerViewHolder?, t: MeetingInfoJson?) {
                val time = (t?.startTime)?.substring(11, 16) + "-" + (t?.completedTime)?.substring(11, 16)

                val isOld = if (t?.completedTime != null) {
                    if (DateHelper.isLessNow(t.completedTime, "yyyy-MM-dd HH:mm:ss") ) {
                        R.mipmap.pic_jieshu
                    }else {
                        R.mipmap.pic_deal
                    }
                }else {
                    R.mipmap.pic_deal
                }

                holder?.setText(R.id.tv_meeting_list_item_time, time)
                        ?.setText(R.id.tv_meeting_list_item_title, t?.subject)
                        ?.setText(R.id.tv_meeting_list_item_meeting_participants, "参加人: ")
                        ?.setImageViewResource(R.id.iv_meeting_list_item_deal, isOld)




                holder?.getView<TextView>(R.id.tv_meeting_list_item_meeting_room)!!.tag = t?.id
                mPresenter.asyncLoadRoomName(
                        holder.getView(R.id.tv_meeting_list_item_meeting_room), t!!.id, t.room)
                holder.getView<TextView>(R.id.tv_meeting_list_item_meeting_originator)!!.tag = t.id + "%%%"
                mPresenter.asyncLoadPersonName(
                        holder.getView(R.id.tv_meeting_list_item_meeting_originator), t.id + "%%%", t.applicant)

                holder.getView<TextView>(R.id.tv_meeting_list_item_meeting_participants).tag = t.id
                var participantsStr = "参加人: "
                for (participants: String in t.inviteMemberList) {
                    participantsStr = participantsStr.concat(participants.split("@").first()).concat(" ")
//                    mPresenter.asyncLoadPersonName(
//                            holder.getView(R.id.tv_meeting_list_item_meeting_participants), t.id, participants)
                }
                holder.getView<TextView>(R.id.tv_meeting_list_item_meeting_participants).text = participantsStr
            }
        }
    }
}