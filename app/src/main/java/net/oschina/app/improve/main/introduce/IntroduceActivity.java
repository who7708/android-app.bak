package net.oschina.app.improve.main.introduce;

import android.content.Context;
import android.content.Intent;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import net.oschina.app.R;
import net.oschina.app.improve.base.activities.BaseActivity;
import net.oschina.app.improve.main.update.OSCSharedPreference;

import butterknife.BindView;

/**
 * 介绍页
 * Created by huanghaibin on 2017/11/24.
 */

public class IntroduceActivity extends BaseActivity {

    @BindView(R.id.viewPager)
    ViewPager mViewPager;

    public static void show(Context context) {
        context.startActivity(new Intent(context, IntroduceActivity.class));
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_introduce;
    }

    @Override
    protected void initWidget() {
        super.initWidget();
        OSCSharedPreference.getInstance().putFirstInstall();
        setSwipeBackEnable(false);
        mViewPager.setAdapter(new FragmentAdapter(getSupportFragmentManager()));
        mViewPager.getAdapter().notifyDataSetChanged();
    }

    private class FragmentAdapter extends FragmentStatePagerAdapter {
        private FragmentAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return position == 0 ? OneFragment.newInstance() : TwoFragment.newInstance();
        }

        @Override
        public int getCount() {
            return 2;
        }
    }

    @Override
    public void onBackPressed() {
        //super.onBackPressed();
    }
}
