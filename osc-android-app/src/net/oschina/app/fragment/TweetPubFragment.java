package net.oschina.app.fragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.oschina.app.AppContext;
import net.oschina.app.R;
import net.oschina.app.base.BaseFragment;
import net.oschina.app.bean.Tweet;
import net.oschina.app.emoji.Emoji;
import net.oschina.app.emoji.EmojiEditText;
import net.oschina.app.emoji.EmojiHelper;
import net.oschina.app.emoji.EmojiViewPagerAdapter;
import net.oschina.app.emoji.EmojiViewPagerAdapter.OnClickEmojiListener;
import net.oschina.app.emoji.SoftKeyboardStateHelper;
import net.oschina.app.emoji.SoftKeyboardStateHelper.SoftKeyboardStateListener;
import net.oschina.app.service.ServerTaskUtils;
import net.oschina.app.ui.dialog.CommonDialog;
import net.oschina.app.ui.dialog.DialogHelper;
import net.oschina.app.util.FileUtil;
import net.oschina.app.util.ImageUtils;
import net.oschina.app.util.SimpleTextWatcher;
import net.oschina.app.util.StringUtils;
import net.oschina.app.util.TDevice;
import net.oschina.app.util.UIHelper;

import org.kymjs.kjframe.KJBitmap;
import org.kymjs.kjframe.bitmap.helper.BitmapCreate;
import org.kymjs.kjframe.http.core.KJAsyncTask;
import org.kymjs.kjframe.utils.FileUtils;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;

import com.viewpagerindicator.CirclePageIndicator;

public class TweetPubFragment extends BaseFragment implements
        SoftKeyboardStateListener, OnClickEmojiListener {

    public static final int ACTION_TYPE_ALBUM = 0;
    public static final int ACTION_TYPE_PHOTO = 1;
    public static final int ACTION_TYPE_RECORD = 2; // 录音
    public static final String FROM_IMAGEPAGE_KEY = "from_image_page";

    public static final String ACTION_TYPE = "action_type";

    private static final int MAX_TEXT_LENGTH = 160;
    private static final String TEXT_ATME = "@请输入用户名 ";
    private static final String TEXT_SOFTWARE = "#请输入软件名#";

    @InjectView(R.id.view_pager)
    ViewPager mViewPager;

    @InjectView(R.id.indicator)
    CirclePageIndicator mIndicator;

    @InjectView(R.id.ib_emoji_keyboard)
    ImageButton mIbEmoji;

    @InjectView(R.id.ib_picture)
    ImageButton mIbPicture;

    @InjectView(R.id.ib_mention)
    ImageButton mIbMention;

    @InjectView(R.id.ib_trend_software)
    ImageButton mIbTrendSoftware;

    @InjectView(R.id.tv_clear)
    TextView mTvClear;

    @InjectView(R.id.ly_emoji)
    View mLyEmoji;

    @InjectView(R.id.rl_img)
    View mLyImage;

    @InjectView(R.id.iv_img)
    ImageView mIvImage;

    @InjectView(R.id.et_content)
    EmojiEditText mEtInput;

    private EmojiViewPagerAdapter mPagerAdapter;
    private SoftKeyboardStateHelper mKeyboardHelper;
    private boolean mIsKeyboardVisible;
    private boolean mNeedHideEmoji;
    private int mCurrentKeyboardHeigh;

    private String theLarge, theThumbnail;
    private File imgFile;

    private final KJBitmap kjb = KJBitmap.create();

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1 && msg.obj != null) {
                // 显示图片
                mIvImage.setImageBitmap((Bitmap) msg.obj);
                mLyImage.setVisibility(View.VISIBLE);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        int mode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        getActivity().getWindow().setSoftInputMode(mode);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.pub_tweet_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.public_menu_send:
            handleSubmit();
            break;
        default:
            break;
        }
        return true;
    }

    /**
     * 方便外部Activity调用
     */
    public void setContentText(String content) {
        if (mEtInput != null) {
            mEtInput.setText(content);
        }
    }

    /**
     * 方便外部Activity调用
     */
    public void setContentImage(String url) {
        handleImageFile(url);
    }

    private void handleSubmit() {
        if (!TDevice.hasInternet()) {
            AppContext.showToastShort(R.string.tip_network_error);
            return;
        }
        if (!AppContext.getInstance().isLogin()) {
            UIHelper.showLoginActivity(getActivity());
            return;
        }
        String content = mEtInput.getText().toString().trim();
        if (TextUtils.isEmpty(content)) {
            mEtInput.requestFocus();
            AppContext.showToastShort(R.string.tip_content_empty);
            return;
        }
        if (content.length() > MAX_TEXT_LENGTH) {
            AppContext.showToastShort(R.string.tip_content_too_long);
            return;
        }

        Tweet tweet = new Tweet();
        tweet.setAuthorid(AppContext.getInstance().getLoginUid());
        tweet.setBody(content);
        if (imgFile != null && imgFile.exists()) {
            tweet.setImageFilePath(imgFile.getAbsolutePath());
        }
        ServerTaskUtils.pubTweet(getActivity(), tweet);
        if (mIsKeyboardVisible) {
            TDevice.hideSoftKeyboard(getActivity().getCurrentFocus());
        }
        getActivity().finish();
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tweet_pub, container,
                false);
        ButterKnife.inject(this, view);
        initView(view);
        mKeyboardHelper = new SoftKeyboardStateHelper(getActivity()
                .findViewById(R.id.container));
        mKeyboardHelper.addSoftKeyboardStateListener(this);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle bundle = getArguments();
        if (bundle != null) {
            int action_type = bundle.getInt(ACTION_TYPE, -1);
            goToSelectPicture(action_type);
            final String imgUrl = bundle.getString(FROM_IMAGEPAGE_KEY);
            handleImageUrl(imgUrl);
        }
    }

    /**
     * 处理从第三方分享跳转来的图片
     * 
     * @param filePath
     */
    private void handleImageFile(final String filePath) {
        if (!StringUtils.isEmpty(filePath)) {
            KJAsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    final Message msg = Message.obtain();
                    msg.what = 1;
                    try {
                        msg.obj = BitmapCreate.bitmapFromStream(
                                new FileInputStream(filePath), 300, 300);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    String path = FileUtils.getSDCardPath()
                            + "/OSChina/tempfile.jpg";
                    FileUtils.bitmapToFile((Bitmap) msg.obj, path);
                    imgFile = new File(path);
                    handler.sendMessage(msg);
                }
            });
        }
    }

    /**
     * 处理从图片浏览跳转来的图片
     * 
     * @param url
     */
    private void handleImageUrl(final String url) {
        if (!StringUtils.isEmpty(url)) {
            KJAsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    final Message msg = Message.obtain();
                    msg.what = 1;
                    msg.obj = kjb.getBitmapFromCache(url);
                    if (msg.obj == null) {
                        msg.obj = kjb.loadBmpMustInThread(url, 300, 300);
                    }
                    String path = FileUtils.getSDCardPath()
                            + "/OSChina/tempfile.jpg";
                    FileUtils.bitmapToFile((Bitmap) msg.obj, path);
                    imgFile = new File(path);
                    handler.sendMessage(msg);
                }
            });
        }
    }

    @Override
    public void initView(View view) {

        mIbEmoji.setOnClickListener(this);
        mIbPicture.setOnClickListener(this);
        mIbMention.setOnClickListener(this);
        mIbTrendSoftware.setOnClickListener(this);
        mTvClear.setOnClickListener(this);
        mTvClear.setText(String.valueOf(MAX_TEXT_LENGTH));
        view.findViewById(R.id.iv_clear_img).setOnClickListener(this);

        mEtInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                    int count) {
                mTvClear.setText((MAX_TEXT_LENGTH - s.length()) + "");
            }
        });
        // 获取保存的tweet草稿
        mEtInput.setText(AppContext.getTweetDraft());
        mEtInput.setSelection(mEtInput.getText().toString().length());

        Map<String, Emoji> emojis = EmojiHelper.qq_emojis_nos;
        // int pagerSize = emojis.size() / 20;
        EmojiHelper.initEmojis();

        List<Emoji> allEmojis = new ArrayList<Emoji>();
        Iterator<String> itr1 = emojis.keySet().iterator();
        while (itr1.hasNext()) {
            Emoji ej = emojis.get(itr1.next());
            allEmojis.add(new Emoji(ej.getResId(), ej.getValue(), ej
                    .getValueNo(), ej.getIndex()));
        }
        Collections.sort(allEmojis);

        List<List<Emoji>> pagers = new ArrayList<List<Emoji>>();
        List<Emoji> es = null;
        int size = 0;
        boolean justAdd = false;
        for (Emoji ej : allEmojis) {
            if (size == 0) {
                es = new ArrayList<Emoji>();
            }
            es.add(new Emoji(ej.getResId(), ej.getValue(), ej.getValueNo()));
            size++;
            if (size == 20) {
                pagers.add(es);
                size = 0;
                justAdd = true;
            } else {
                justAdd = false;
            }
        }
        if (!justAdd && es != null) {
            pagers.add(es);
        }

        int emojiHeight = caculateEmojiPanelHeight();

        mPagerAdapter = new EmojiViewPagerAdapter(getActivity(), pagers,
                emojiHeight, this);
        mViewPager.setAdapter(mPagerAdapter);
        mIndicator.setViewPager(mViewPager);
    }

    @Override
    public boolean onBackPressed() {
        if (mLyEmoji.getVisibility() == View.VISIBLE) {
            hideEmojiPanel();
            return true;
        }
        final String tweet = mEtInput.getText().toString();
        if (!TextUtils.isEmpty(tweet)) {
            CommonDialog dialog = DialogHelper
                    .getPinterestDialogCancelable(getActivity());
            dialog.setMessage(R.string.draft_tweet_message);
            dialog.setNegativeButton(R.string.cancle, new OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    AppContext.setTweetDraft("");
                    getActivity().finish();
                }
            });
            dialog.setPositiveButton(R.string.ok, new OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    AppContext.setTweetDraft(tweet);
                    getActivity().finish();
                }
            });
            dialog.show();
            return true;
        }
        return super.onBackPressed();
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        if (id == R.id.ib_emoji_keyboard) {
            if (mLyEmoji.getVisibility() == View.GONE) {
                mNeedHideEmoji = true;
                tryShowEmojiPanel();
            } else {
                tryHideEmojiPanel();
            }
        } else if (id == R.id.ib_picture) {
            handleSelectPicture();
        } else if (id == R.id.ib_mention) {
            insertMention();
        } else if (id == R.id.ib_trend_software) {
            insertTrendSoftware();
        } else if (id == R.id.tv_clear) {
            handleClearWords();
        } else if (id == R.id.iv_clear_img) {
            mIvImage.setImageBitmap(null);
            mLyImage.setVisibility(View.GONE);
            imgFile = null;
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode,
            final Intent imageReturnIntent) {
        if (resultCode != Activity.RESULT_OK)
            return;
        new Thread() {
            private String selectedImagePath;

            @Override
            public void run() {
                Bitmap bitmap = null;

                if (requestCode == ImageUtils.REQUEST_CODE_GETIMAGE_BYSDCARD) {
                    if (imageReturnIntent == null)
                        return;
                    Uri selectedImageUri = imageReturnIntent.getData();
                    if (selectedImageUri != null) {
                        selectedImagePath = ImageUtils.getImagePath(
                                selectedImageUri, getActivity());
                    }

                    if (selectedImagePath != null) {
                        theLarge = selectedImagePath;
                    } else {
                        bitmap = ImageUtils.loadPicasaImageFromGalley(
                                selectedImageUri, getActivity());
                    }

                    if (AppContext
                            .isMethodsCompat(android.os.Build.VERSION_CODES.ECLAIR_MR1)) {
                        String imaName = FileUtil.getFileName(theLarge);
                        if (imaName != null)
                            bitmap = ImageUtils.loadImgThumbnail(getActivity(),
                                    imaName,
                                    MediaStore.Images.Thumbnails.MICRO_KIND);
                    }
                    if (bitmap == null && !StringUtils.isEmpty(theLarge))
                        bitmap = ImageUtils
                                .loadImgThumbnail(theLarge, 100, 100);
                } else if (requestCode == ImageUtils.REQUEST_CODE_GETIMAGE_BYCAMERA) {
                    // 拍摄图片
                    if (bitmap == null && !StringUtils.isEmpty(theLarge)) {
                        bitmap = ImageUtils
                                .loadImgThumbnail(theLarge, 100, 100);
                    }
                }

                if (bitmap != null) {// 存放照片的文件夹
                    String savePath = Environment.getExternalStorageDirectory()
                            .getAbsolutePath() + "/OSChina/Camera/";
                    File savedir = new File(savePath);
                    if (!savedir.exists()) {
                        savedir.mkdirs();
                    }

                    String largeFileName = FileUtil.getFileName(theLarge);
                    String largeFilePath = savePath + largeFileName;
                    // 判断是否已存在缩略图
                    if (largeFileName.startsWith("thumb_")
                            && new File(largeFilePath).exists()) {
                        theThumbnail = largeFilePath;
                        imgFile = new File(theThumbnail);
                    } else {
                        // 生成上传的800宽度图片
                        String thumbFileName = "thumb_" + largeFileName;
                        theThumbnail = savePath + thumbFileName;
                        if (new File(theThumbnail).exists()) {
                            imgFile = new File(theThumbnail);
                        } else {
                            try {
                                // 压缩上传的图片
                                ImageUtils.createImageThumbnail(getActivity(),
                                        theLarge, theThumbnail, 800, 80);
                                imgFile = new File(theThumbnail);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    // 保存动弹临时图片
                    // ((AppContext) getApplication()).setProperty(
                    // tempTweetImageKey, theThumbnail);

                    Message msg = new Message();
                    msg.what = 1;
                    msg.obj = bitmap;
                    handler.sendMessage(msg);
                }
            };
        }.start();
    }

    private void handleClearWords() {
        if (TextUtils.isEmpty(mEtInput.getText().toString()))
            return;
        final CommonDialog dialog = DialogHelper
                .getPinterestDialogCancelable(getActivity());
        dialog.setMessage(R.string.clearwords);
        dialog.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        mEtInput.getText().clear();
                        if (mIsKeyboardVisible) {
                            TDevice.showSoftKeyboard(mEtInput);
                        }
                    }
                });
        dialog.setNegativeButton(R.string.cancle, null);
        dialog.show();
    }

    private void handleSelectPicture() {
        final CommonDialog dialog = DialogHelper
                .getPinterestDialogCancelable(getActivity());
        dialog.setTitle(R.string.choose_picture);
        dialog.setNegativeButton(R.string.cancle, null);
        dialog.setItemsWithoutChk(
                getResources().getStringArray(R.array.choose_picture),
                new OnItemClickListener() {

                    @Override
                    public void onItemClick(AdapterView<?> parent, View view,
                            int position, long id) {
                        dialog.dismiss();
                        goToSelectPicture(position);
                    }
                });
        dialog.show();
    }

    private void goToSelectPicture(int position) {
        switch (position) {
        case ACTION_TYPE_ALBUM:
            Intent intent;
            if (Build.VERSION.SDK_INT < 19) {
                intent = new Intent();
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, "选择图片"),
                        ImageUtils.REQUEST_CODE_GETIMAGE_BYSDCARD);
            } else {
                intent = new Intent(Intent.ACTION_PICK,
                        Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, "选择图片"),
                        ImageUtils.REQUEST_CODE_GETIMAGE_BYSDCARD);
            }
            break;
        case ACTION_TYPE_PHOTO:
            // 判断是否挂载了SD卡
            String savePath = "";
            String storageState = Environment.getExternalStorageState();
            if (storageState.equals(Environment.MEDIA_MOUNTED)) {
                savePath = Environment.getExternalStorageDirectory()
                        .getAbsolutePath() + "/oschina/Camera/";
                File savedir = new File(savePath);
                if (!savedir.exists()) {
                    savedir.mkdirs();
                }
            }

            // 没有挂载SD卡，无法保存文件
            if (StringUtils.isEmpty(savePath)) {
                AppContext.showToastShort("无法保存照片，请检查SD卡是否挂载");
                return;
            }

            String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss")
                    .format(new Date());
            String fileName = "osc_" + timeStamp + ".jpg";// 照片命名
            File out = new File(savePath, fileName);
            Uri uri = Uri.fromFile(out);

            theLarge = savePath + fileName;// 该照片的绝对路径

            intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            startActivityForResult(intent,
                    ImageUtils.REQUEST_CODE_GETIMAGE_BYCAMERA);
            break;
        default:
            break;
        }
    }

    private void tryShowEmojiPanel() {
        if (mIsKeyboardVisible) {
            TDevice.hideSoftKeyboard(getActivity().getCurrentFocus());
        } else {
            showEmojiPanel();
        }
    }

    private void showEmojiPanel() {
        mNeedHideEmoji = false;
        mLyEmoji.setVisibility(View.VISIBLE);
        mIbEmoji.setImageResource(R.drawable.compose_toolbar_keyboard_selector);
    }

    private void tryHideEmojiPanel() {
        if (!mIsKeyboardVisible) {
            TDevice.showSoftKeyboard(mEtInput);
        } else {
            hideEmojiPanel();
        }
    }

    private void hideEmojiPanel() {
        if (mLyEmoji.getVisibility() == View.VISIBLE) {
            mLyEmoji.setVisibility(View.GONE);
            mIbEmoji.setImageResource(R.drawable.compose_toolbar_emoji_selector);
        }
    }

    @Override
    public void onSoftKeyboardOpened(int keyboardHeightInPx) {
        mIsKeyboardVisible = true;
        hideEmojiPanel();
    }

    @Override
    public void onSoftKeyboardClosed() {
        mIsKeyboardVisible = false;
        if (mNeedHideEmoji) {
            showEmojiPanel();
        }
    }

    private int caculateEmojiPanelHeight() {
        if (mCurrentKeyboardHeigh == 0) {
            mCurrentKeyboardHeigh = (int) TDevice.dpToPixel(180);
        }
        int emojiPanelHeight = (int) (mCurrentKeyboardHeigh - TDevice
                .dpToPixel(20));
        int emojiHeight = emojiPanelHeight / 3;

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, emojiPanelHeight);
        mViewPager.setLayoutParams(lp);
        if (mPagerAdapter != null) {
            mPagerAdapter.setEmojiHeight(emojiHeight);
        }
        return emojiHeight;
    }

    @Override
    public void onEmojiClick(Emoji emoji) {
        mEtInput.insertEmoji(emoji);
    }

    @Override
    public void onDelete() {
        mEtInput.delete();
    }

    private void insertMention() {
        TDevice.showSoftKeyboard(mEtInput);
        // 在光标所在处插入“@用户名”
        int curTextLength = mEtInput.getText().length();
        if (curTextLength >= MAX_TEXT_LENGTH)
            return;
        String atme = TEXT_ATME;
        int start, end;
        if ((MAX_TEXT_LENGTH - curTextLength) >= atme.length()) {
            start = mEtInput.getSelectionStart() + 1;
            end = start + atme.length() - 2;
        } else {
            int num = MAX_TEXT_LENGTH - curTextLength;
            if (num < atme.length()) {
                atme = atme.substring(0, num);
            }
            start = mEtInput.getSelectionStart() + 1;
            end = start + atme.length() - 1;
        }
        if (start > MAX_TEXT_LENGTH || end > MAX_TEXT_LENGTH) {
            start = MAX_TEXT_LENGTH;
            end = MAX_TEXT_LENGTH;
        }
        mEtInput.getText().insert(mEtInput.getSelectionStart(), atme);
        mEtInput.setSelection(start, end);// 设置选中文字
    }

    private void insertTrendSoftware() {
        // 在光标所在处插入“#软件名#”
        int curTextLength = mEtInput.getText().length();
        if (curTextLength >= MAX_TEXT_LENGTH)
            return;
        String software = TEXT_SOFTWARE;
        int start, end;
        if ((MAX_TEXT_LENGTH - curTextLength) >= software.length()) {
            start = mEtInput.getSelectionStart() + 1;
            end = start + software.length() - 2;
        } else {
            int num = MAX_TEXT_LENGTH - curTextLength;
            if (num < software.length()) {
                software = software.substring(0, num);
            }
            start = mEtInput.getSelectionStart() + 1;
            end = start + software.length() - 1;
        }
        if (start > MAX_TEXT_LENGTH || end > MAX_TEXT_LENGTH) {
            start = MAX_TEXT_LENGTH;
            end = MAX_TEXT_LENGTH;
        }
        mEtInput.getText().insert(mEtInput.getSelectionStart(), software);
        mEtInput.setSelection(start, end);// 设置选中文字
    }

    @Override
    public void initData() {}
}
