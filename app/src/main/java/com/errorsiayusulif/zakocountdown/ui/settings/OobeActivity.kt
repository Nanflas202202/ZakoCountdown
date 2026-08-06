package com.errorsiayusulif.zakocountdown.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.errorsiayusulif.zakocountdown.MainActivity
import com.errorsiayusulif.zakocountdown.data.PreferenceManager
import com.errorsiayusulif.zakocountdown.databinding.ActivityOobeBinding

class OobeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOobeBinding
    private lateinit var preferenceManager: PreferenceManager

    // 记录EULA是否被同意
    var isEulaChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOobeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferenceManager = PreferenceManager(this)

        setupViewPager()
        setupButtons()
    }

    private fun setupViewPager() {
        // 禁用滑动，强制用户点击按钮进行下一步（防止跳过EULA或权限）
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 5
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> OobeWelcomeFragment()
                    1 -> OobeEulaFragment()
                    2 -> OobePermissionsFragment()
                    3 -> OobeCustomizationFragment()
                    4 -> OobeTutorialFragment()
                    else -> OobeWelcomeFragment()
                }
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateBottomBar(position)
            }
        })
    }

    private fun setupButtons() {
        binding.btnPrev.setOnClickListener {
            if (binding.viewPager.currentItem > 0) {
                binding.viewPager.currentItem -= 1
            }
        }

        binding.btnNext.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem == 4) {
                // 最后一页，完成 OOBE
                preferenceManager.setOobeCompleted(true)
                preferenceManager.setEulaAccepted(true)

                // --- 【核心修复】使用安全的方式拉起应用主界面 ---
                var launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent == null) {
                    launchIntent = Intent(this, MainActivity::class.java)
                }

                // 清空任务栈，防止按返回键又回到 OOBE
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(launchIntent)
                finish()
            } else {
                binding.viewPager.currentItem += 1
            }
        }
    }

    fun updateBottomBar(position: Int) {
        binding.btnPrev.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE

        when (position) {
            0 -> { // 欢迎
                binding.btnNext.text = "开始配置"
                binding.btnNext.isEnabled = true
            }
            1 -> { // EULA
                binding.btnNext.text = "我同意"
                binding.btnNext.isEnabled = isEulaChecked
            }
            2 -> { // 权限
                binding.btnNext.text = "下一步"
                binding.btnNext.isEnabled = true
            }
            3 -> { // 自定义
                binding.btnNext.text = "下一步"
                binding.btnNext.isEnabled = true
            }
            4 -> { // 教程
                binding.btnNext.text = "进入应用"
                binding.btnNext.isEnabled = true
            }
        }
    }
}