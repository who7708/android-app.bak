package net.oschina.app.improve.write;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import net.oschina.app.R;
import net.oschina.app.improve.base.activities.BaseBackActivity;
import net.oschina.app.improve.widget.SimplexToast;
import net.oschina.app.improve.widget.rich.RichEditLayout;
import net.oschina.app.improve.widget.rich.RichEditText;
import net.oschina.app.improve.widget.rich.Section;
import net.oschina.app.improve.widget.rich.TextSection;

import java.util.List;

import butterknife.Bind;
import butterknife.OnClick;

/**
 * 写博客界面
 * Created by huanghaibin on 2017/8/3.
 */

public class WriteActivity extends BaseBackActivity implements
        View.OnClickListener, FontPopupWindow.OnFontChangeListener,
        AlignPopupWindow.OnAlignChangeListener, RichEditText.OnSectionChangeListener,
        HPopupWindow.OnHeaderChangeListener,
        WriteContract.View {

    @Bind(R.id.fl_content)
    FrameLayout mFrameContent;

    @Bind(R.id.richLayout)
    RichEditLayout mEditView;

    private CategoryFragment mCategoryFragment;
    private AlignPopupWindow mAlignWindow;
    private HPopupWindow mHPopupWindow;
    private FontPopupWindow mFontPopupWindow;
    private Handler mHandler = new Handler();

    private WritePresenter mPresenter;

    private Blog mBlog = new Blog();

    public static void show(Context context) {
        context.startActivity(new Intent(context, WriteActivity.class));
    }

    @Override
    protected int getContentView() {
        return R.layout.activity_write;
    }

    @Override
    protected void initWidget() {
        super.initWidget();
        mEditView.setContentPanel(mFrameContent);
        mCategoryFragment = CategoryFragment.newInstance();
        addFragment(R.id.fl_content, mCategoryFragment);
        mEditView.setOnSectionChangeListener(this);
        mAlignWindow = new AlignPopupWindow(this, this);
        mFontPopupWindow = new FontPopupWindow(this, this);
        mHPopupWindow = new HPopupWindow(this, this);
        mBlog.setTitle("博客标题");
        mBlog.setSummary("博客摘要");
    }

    @Override
    protected void initData() {
        super.initData();
        mPresenter = new WritePresenter(this);
    }

    @OnClick({R.id.btn_keyboard, R.id.btn_font, R.id.btn_align, R.id.btn_h,
            R.id.btn_category})
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_keyboard:
                if (mEditView.isKeyboardOpen() && mFrameContent.getVisibility() == View.GONE) {
                    mHandler.removeCallbacksAndMessages(null);
                    mEditView.closeKeyboard();
                    mFrameContent.setVisibility(View.GONE);
                    mEditView.setCategoryTint(0xff111111);
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mEditView.setAdjustResize();
                        }
                    }, 150);
                } else {
                    if (mFrameContent.getVisibility() == View.VISIBLE) {
                        mEditView.setAdjustNothing();
                        mEditView.setCategoryTint(0xff111111);
                    } else {
                        mEditView.setAdjustResize();
                    }
                    mEditView.openKeyboard();
                }
                break;
            case R.id.btn_category:
                mEditView.setAdjustNothing();
                if (mFrameContent.getVisibility() == View.VISIBLE) {
                    if (mEditView.isKeyboardOpen()) {
                        mEditView.closeKeyboard();
                        addFragment(R.id.fl_content, mCategoryFragment);
                        mFrameContent.setVisibility(View.VISIBLE);
                        mEditView.setCategoryTint(0xff24cf5f);
                    } else {
                        addFragment(R.id.fl_content, mCategoryFragment);
                        mFrameContent.setVisibility(View.GONE);
                        mEditView.setCategoryTint(0xff111111);
                        mEditView.closeKeyboard();
                    }

                } else {
                    mEditView.closeKeyboard();
                    addFragment(R.id.fl_content, mCategoryFragment);
                    mFrameContent.setVisibility(View.VISIBLE);
                    mEditView.setCategoryTint(0xff24cf5f);
                }
                break;
            case R.id.btn_font:
                mFontPopupWindow.show(v);
                break;
            case R.id.btn_h:
                mHPopupWindow.show(v);
                break;
            case R.id.btn_align:
                mAlignWindow.show(v);
                break;

        }
    }

    @Override
    public void onSectionChange(TextSection section) {
        mAlignWindow.setStyle(section);
        mFontPopupWindow.setStyle(section);
        mHPopupWindow.setStyle(section);
    }


    @Override
    public void onBoldChange(boolean isBold) {
        mEditView.setBold(isBold);
    }

    @Override
    public void onItalicChange(boolean isItalic) {
        mEditView.setItalic(isItalic);
    }

    @Override
    public void onMidLineChange(boolean isMidLine) {
        mEditView.setMidLine(isMidLine);
    }

    @Override
    public void onAlignChange(int align) {
        mEditView.setAlignStyle(align);
    }

    @Override
    public void onTitleChange(int size) {
        mEditView.setTextSize(size);
    }


    @Override
    public void showNetworkError(int strId) {
        if (isDestroy()) return;
        SimplexToast.show(this, strId);
    }

    @Override
    public void showPubBlogSuccess(int strId, Blog blog) {
        if (isDestroy()) return;
        SimplexToast.show(this, strId);
    }

    @Override
    public void showPubBlogFailure(int strId) {
        if (isDestroy()) return;
        SimplexToast.show(this, strId);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_commit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_commit) {
            List<TextSection> sections = mEditView.createSectionList();
            if (sections != null) {
                mPresenter.pubBlog(mBlog,sections);
            }
        }
        return false;
    }

    @Override
    public void setPresenter(WriteContract.Presenter presenter) {

    }

}
