/*
 * Copyright 2018 Bakumon. https://github.com/Bakumon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package me.bakumon.moneykeeper.ui.add

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.os.Bundle
import android.support.v7.app.AppCompatDelegate
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.list.customListAdapter
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog
import kotlinx.android.synthetic.main.fragment_option.*
import me.bakumon.moneykeeper.ConfigManager
import me.bakumon.moneykeeper.R
import me.bakumon.moneykeeper.Router
import me.bakumon.moneykeeper.database.entity.Assets
import me.bakumon.moneykeeper.database.entity.RecordWithType
import me.bakumon.moneykeeper.ui.assets.transfer.AssetsChooseViewBinder
import me.bakumon.moneykeeper.ui.common.BaseFragment
import me.bakumon.moneykeeper.utill.DateUtils
import me.bakumon.moneykeeper.utill.ResourcesUtil
import me.bakumon.moneykeeper.utill.SoftInputUtils
import me.drakeet.floo.Floo
import me.drakeet.multitype.Items
import me.drakeet.multitype.MultiTypeAdapter
import me.drakeet.multitype.register
import java.util.*

/**
 * 记一笔界面 的 "选择日期、备注、选择账户" 抽离成单独的 Fragment
 * 对外提供的功能：
 * 1.回显要修改的数据
 * 2.获取当前选择的日期
 * 3.获取备注、清除备注
 * 4.获取新选的账户、老账户
 * 5.控制可选择账户
 * @author Bakumon https://bakumon.me
 */
class OptionFragment : BaseFragment() {

    private lateinit var mViewModel: OptionViewModel

    private var mCurrentChooseDate: Date? = DateUtils.getTodayDate()
    private val mCurrentChooseCalendar = Calendar.getInstance()
    private var mRecord: RecordWithType? = null

    private var mOnEditDoneListener: (() -> Unit)? = null

    private var mAssets: Assets? = null
    private var mOldAssets: Assets? = null

    override val layoutId: Int
        get() = R.layout.fragment_option

    override fun lazyInitData() {}

    override fun onInit(savedInstanceState: Bundle?) {
        // 选择日期
        tvDate.setOnClickListener { showDatePickerDialog() }
        mRecord = arguments?.getSerializable(Router.ExtraKey.KEY_RECORD_BEAN) as RecordWithType?
        mViewModel = getViewModel()
        edtRemark.setOnEditorActionListener { _, _, _ ->
            SoftInputUtils.hideSoftInput(edtRemark)
            mOnEditDoneListener?.invoke()
            false
        }
        if (mRecord == null) {
            // 新增
            // 设置资产
            val assetsId = ConfigManager.assetId
            if (assetsId == -1) {
                updateAccountView(name = getString(R.string.text_no_choose_account))
            } else {
                getAssetsAccount(assetsId)
            }
        } else {
            // 修改
            // 设置资产
            if (mRecord!!.assetsId == -1 || mRecord!!.assetsId == null) {
                updateAccountView(name = getString(R.string.text_no_choose_account))
            } else {
                getAssetsAccount(mRecord!!.assetsId!!)
            }
            // 回显数据
            edtRemark.setText(mRecord!!.remark)
            mCurrentChooseDate = mRecord!!.time
            mCurrentChooseCalendar.time = mCurrentChooseDate
            tvDate.text = DateUtils.getWordTime(mCurrentChooseDate!!)
        }
        llRecordAccount.setOnClickListener { chooseAccount() }
    }

    private var isDialogShow = false

    private fun showDatePickerDialog() {
        if (isDialogShow) {
            return
        }
        isDialogShow = true
        val dpd = DatePickerDialog.newInstance(
                { _, year, monthOfYear, dayOfMonth ->
                    mCurrentChooseDate = DateUtils.getDate(year, monthOfYear + 1, dayOfMonth)
                    mCurrentChooseCalendar.time = mCurrentChooseDate
                    tvDate.text = DateUtils.getWordTime(mCurrentChooseDate!!)
                }, mCurrentChooseCalendar)
        dpd.maxDate = Calendar.getInstance()
        dpd.isThemeDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES

        @Suppress("DEPRECATION")
        dpd.show(activity!!.fragmentManager, TAG_PICKER_DIALOG)

        dpd.setOnDismissListener { isDialogShow = false }
    }

    private var assetsLiveData: LiveData<Assets>? = null

    private fun getAssetsAccount(id: Int) {
        assetsLiveData = mViewModel.getAssetsById(id)
        assetsLiveData!!.observe(this, Observer {
            if (it == null) {
                updateAccountView(name = getString(R.string.text_no_choose_account))
            } else {
                mAssets = it
                if (mRecord != null) {
                    // 修改
                    mOldAssets = it
                }
                updateAccountView(it.imgName, it.name)
            }
        })
    }

    private fun updateAccountView(imgName: String = "", name: String) {
        if (imgName.isBlank()) {
            ivRecordAccount.visibility = View.GONE
        } else {
            ivRecordAccount.visibility = View.VISIBLE
            ivRecordAccount.setImageResource(ResourcesUtil.getTypeImgId(context!!, imgName))
        }
        tvRecordAccount.text = name

        // 防止数据库改变 assetsLiveData 自动 observe，导致 账户显示 oldAssets 的 name
        assetsLiveData?.removeObservers(this)
    }

    private var assetsListLiveData: LiveData<List<Assets>>? = null

    private fun chooseAccount() {
        assetsListLiveData = mViewModel.getAssets()
        assetsListLiveData!!.observe(this, Observer {
            if (it != null) {
                showListDialog(it)
            }
        })
    }

    private var mDialog: MaterialDialog? = null
    private fun showListDialog(list: List<Assets>) {
        val adapter = MultiTypeAdapter()
        adapter.register(Assets::class, AssetsChooseViewBinder { assetsItemClick(it) })
        adapter.register(NoAccount::class, AssetsNoChooseViewBinder { noAssetsItemClick(it) })
        val items = Items()
        items.addAll(list)
        items.add(NoAccount(0, getString(R.string.text_no_choose_account), getString(R.string.text_no_choose_account_tip)))
        items.add(NoAccount(1, getString(R.string.text_new_choose_account)))
        adapter.items = items

        if (isDialogShow) {
            return
        }
        isDialogShow = true
        mDialog = MaterialDialog(context!!)
                .title(R.string.text_choose_account)
                .customListAdapter(adapter)
                .positiveButton(res = R.string.text_cancel)
                .onDismiss {
                    isDialogShow = false
                    // 防止数据库改变 assetsListLiveData 自动 observe，导致 dialog 重复弹起
                    assetsListLiveData?.removeObservers(this)
                }
        mDialog?.show()
    }

    private fun assetsItemClick(assets: Assets) {
        mAssets = assets
        updateAccountView(assets.imgName, assets.name)
        // 更新上次使用的 assets id
        ConfigManager.setAssetsId(assets.id!!)
        mDialog?.dismiss()
    }

    private fun noAssetsItemClick(noAccount: NoAccount) {
        if (noAccount.type == 0) {
            // 不选择账户
            mAssets = null
            updateAccountView(name = getString(R.string.text_no_choose_account))
            // 更新上次使用的 assets id
            ConfigManager.setAssetsId(-1)
        } else {
            // 添加账户
            Floo.navigation(context!!, Router.Url.URL_CHOOSE_ASSETS).start()
        }
        mDialog?.dismiss()
    }

    fun setEditDoneListener(onEditDoneListener: (() -> Unit)) {
        mOnEditDoneListener = onEditDoneListener
    }

    fun setAssetsVisibility(visibility: Boolean) {
        if (llRecordAccount != null) {
            llRecordAccount.visibility = if (visibility) View.VISIBLE else View.GONE
        }
    }

    fun getOldAssets(): Assets? {
        return mOldAssets
    }

    fun getNewAssets(): Assets? {
        return mAssets
    }

    fun getRemark(): String {
        return edtRemark.text.toString().trim()
    }

    fun clearRemark() {
        edtRemark.setText("")
    }

    fun getDate(): Date? {
        return mCurrentChooseDate
    }

    companion object {
        private const val TAG_PICKER_DIALOG = "Datepickerdialog"
        fun newInstance(record: RecordWithType? = null): OptionFragment {
            val fragment = OptionFragment()
            val bundle = Bundle()
            bundle.putSerializable(Router.ExtraKey.KEY_RECORD_BEAN, record)
            fragment.arguments = bundle
            return fragment
        }
    }
}
