package net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.bbs.reply

import android.text.TextUtils
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.component.api.ExceptionHandler
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.O2SDKManager
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.base.BasePresenterImpl
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.component.api.ResponseHandler
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.IdData
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.bbs.ReplyFormJson
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.bbs.SubjectReplyInfoJson
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.XLog
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.ZoneUtil
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import rx.Observable
import rx.Subscriber
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.File

class BBSReplyPresenter : BasePresenterImpl<BBSReplyContract.View>(), BBSReplyContract.Presenter {

    override fun getReplyParentInfo(parentId: String) {
        if (TextUtils.isEmpty(parentId)) {
            mView?.getReplyParentFail()
            return
        }
        getBBSAssembleControlService(mView?.getContext())?.let { service ->
            service.getSubjectReplyInfo(parentId)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(ResponseHandler<SubjectReplyInfoJson> { info -> mView?.getReplyParentSuccess(info) },
                            ExceptionHandler(mView?.getContext()) { e ->
                                XLog.error("", e)
                                mView?.getReplyParentFail()
                            })

        }
    }

    override fun postReply(form: ReplyFormJson) {
        val json = O2SDKManager.instance().gson.toJson(form)
        val body = RequestBody.create(MediaType.parse("text/json"), json)
        getBBSAssembleControlService(mView?.getContext())?.let { service ->
            service.publishReply(body)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(ResponseHandler<IdData> { id -> mView?.publishReplySuccess(id.id) },
                            ExceptionHandler(mView?.getContext()) { e ->
                                XLog.error("", e)
                                mView?.publishReplyFail()
                            })
        }
    }

    override fun uploadImage(filePath: String, newReplyId: String) {
        getBBSAssembleControlService(mView?.getContext())?.let { service ->
            Observable.create(object : Observable.OnSubscribe<File> {
                override fun call(t: Subscriber<in File>?) {
                    try {
                        val path = ZoneUtil.compressBBSImage(filePath, mView?.getContext())
                        val file = File(path)
                        t?.onNext(file)
                    } catch (e: Exception) {
                        t?.onError(e)
                    }
                    t?.onCompleted()
                }
            }).subscribeOn(Schedulers.io())
                    .flatMap { file ->
                        val requestBody = RequestBody.create(MediaType.parse("application/octet-stream"), file)
                        val body = MultipartBody.Part.createFormData("file", file.name, requestBody)
                        val siteBody = RequestBody.create(MediaType.parse("text/plain"), newReplyId)
                        service.uploadBBSSubjectAttachment(body, siteBody, newReplyId)
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(ResponseHandler<IdData> { id ->
                        mView?.uploadSuccess(id.id, filePath)
                    }, ExceptionHandler(mView?.getContext()) { e ->
                        XLog.error("", e)
                        mView?.uploadFail(filePath)
                    })
        }
    }
}
