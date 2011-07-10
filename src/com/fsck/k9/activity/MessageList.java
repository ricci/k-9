package com.fsck.k9.activity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Config;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.fsck.k9.Account;
import com.fsck.k9.AccountStats;
import com.fsck.k9.FontSizes;
import com.fsck.k9.K9;
import com.fsck.k9.Preferences;
import com.fsck.k9.R;
import com.fsck.k9.SearchSpecification;
import com.fsck.k9.activity.setup.AccountSettings;
import com.fsck.k9.activity.setup.FolderSettings;
import com.fsck.k9.activity.setup.Prefs;
import com.fsck.k9.controller.MessagingController;
import com.fsck.k9.controller.MessagingController.SORT_TYPE;
import com.fsck.k9.controller.MessagingListener;
import com.fsck.k9.crypto.PgpData;
import com.fsck.k9.helper.Contacts;
import com.fsck.k9.helper.FileBrowserHelper;
import com.fsck.k9.helper.FileBrowserHelper.FileBrowserFailOverCallback;
import com.fsck.k9.helper.MessageHelper;
import com.fsck.k9.helper.Utility;
import com.fsck.k9.mail.*;
import com.fsck.k9.mail.store.LocalStore;
import com.fsck.k9.mail.store.LocalStore.LocalFolder;
import com.fsck.k9.mail.store.StorageManager;

import com.fsck.k9.view.AttachmentView.AttachmentFileDownloadCallback;
import com.fsck.k9.view.SingleMessageView;
import com.fsck.k9.view.AttachmentView;
import com.fsck.k9.view.ToggleScrollView;

import com.fsck.splitview.SplitView;

import com.markupartist.android.widget.ActionBar;
import com.markupartist.android.widget.ActionBar.AbstractAction;
import net.londatiga.android.QuickAction;
import net.londatiga.android.ActionItem;

/**
 * MessageList is the primary user interface for the program. This Activity
 * shows a list of messages.
 * From this Activity the user can perform all standard message operations.
 */
public class MessageList
    extends K9Activity
    implements OnClickListener, AdapterView.OnItemClickListener, AnimationListener {

    /**
     * Reverses the result of a {@link Comparator}.
     *
     * @param <T>
     */
    public static class ReverseComparator<T> implements Comparator<T> {
        private Comparator<T> mDelegate;

        /**
         * @param delegate
         *            Never <code>null</code>.
         */
        public ReverseComparator(final Comparator<T> delegate) {
            mDelegate = delegate;
        }

        @Override
        public int compare(final T object1, final T object2) {
            // arg1 & 2 are mixed up, this is done on purpose
            return mDelegate.compare(object2, object1);
        }

    }

    /**
     * Chains comparator to find a non-0 result.
     *
     * @param <T>
     */
    public static class ComparatorChain<T> implements Comparator<T> {

        private List<Comparator<T>> mChain;

        /**
         * @param chain
         *            Comparator chain. Never <code>null</code>.
         */
        public ComparatorChain(final List<Comparator<T>> chain) {
            mChain = chain;
        }

        @Override
        public int compare(T object1, T object2) {
            int result = 0;
            for (final Comparator<T> comparator : mChain) {
                result = comparator.compare(object1, object2);
                if (result != 0) {
                    break;
                }
            }
            return result;
        }

    }

    public static class AttachmentComparator implements Comparator<MessageInfoHolder> {

        @Override
        public int compare(MessageInfoHolder object1, MessageInfoHolder object2) {
            return (object1.message.hasAttachments() ? 0 : 1) - (object2.message.hasAttachments() ? 0 : 1);
        }

    }

    public static class FlaggedComparator implements Comparator<MessageInfoHolder> {

        @Override
        public int compare(MessageInfoHolder object1, MessageInfoHolder object2) {
            return (object1.flagged ? 0 : 1) - (object2.flagged ? 0 : 1);
        }

    }

    public static class UnreadComparator implements Comparator<MessageInfoHolder> {

        @Override
        public int compare(MessageInfoHolder object1, MessageInfoHolder object2) {
            return (object1.read ? 1 : 0) - (object2.read ? 1 : 0);
        }

    }

    public static class SenderComparator implements Comparator<MessageInfoHolder> {

        @Override
        public int compare(MessageInfoHolder object1, MessageInfoHolder object2) {
            return object1.compareCounterparty.toLowerCase().compareTo(object2.compareCounterparty.toLowerCase());
        }

    }

    public static class DateComparator implements Comparator<MessageInfoHolder> {

        @Override
        public int compare(MessageInfoHolder object1, MessageInfoHolder object2) {
            return object1.compareDate.compareTo(object2.compareDate);
        }

    }

    public static class SubjectComparator implements Comparator<MessageInfoHolder> {

        @Override
        public int compare(MessageInfoHolder arg0, MessageInfoHolder arg1) {
            // XXX doesn't respect the Comparator contract since it alters the compared object
            if (arg0.compareSubject == null) {
                arg0.compareSubject = Utility.stripSubject(arg0.message.getSubject());
            }
            if (arg1.compareSubject == null) {
                arg1.compareSubject = Utility.stripSubject(arg1.message.getSubject());
            }
            return arg0.compareSubject.compareToIgnoreCase(arg1.compareSubject);
        }

    }

    /**
     * Immutable empty {@link Message} array
     */
    private static final Message[] EMPTY_MESSAGE_ARRAY = new Message[0];

    private static final int DIALOG_MARK_ALL_AS_READ = 1;

    private static final int ACTIVITY_CHOOSE_FOLDER_MOVE = 1;
    private static final int ACTIVITY_CHOOSE_FOLDER_COPY = 2;
    private static final int ACTIVITY_CHOOSE_DIRECTORY = 5;


    private static final String SHOW_PICTURES = "showPictures";
    private static final String STATE_PGP_DATA = "pgpData";
    private static final String EXTRA_ACCOUNT = "account";
    private static final String EXTRA_FOLDER  = "folder";
    private static final String EXTRA_QUERY = "query";
    private static final String EXTRA_QUERY_FLAGS = "queryFlags";
    private static final String EXTRA_FORBIDDEN_FLAGS = "forbiddenFlags";
    private static final String EXTRA_INTEGRATE = "integrate";
    private static final String EXTRA_ACCOUNT_UUIDS = "accountUuids";
    private static final String EXTRA_FOLDER_NAMES = "folderNames";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_LIST_POSITION = "listPosition";
    private static final String EXTRA_NEXT = "com.fsck.k9.MessageView_next";


    private MessageInfoHolder mNextMessage;
    private MessageInfoHolder mPreviousMessage;
    private MessageInfoHolder mCurrentMessageInfo;
    private ToggleScrollView mToggleScrollView;
    private SingleMessageView mMessageView;
    private SplitView mSplitView;
    private Message mMessage;
    private PgpData mPgpData = null;



    private View mNext;
    private View mPrevious;
    private View mDelete;
    private View mMove;
    private Contacts mContacts;



    private static final int PREVIOUS = 1;
    private static final int NEXT = 2;
    private int mLastDirection = PREVIOUS;


    /**
     * Maps a {@link SORT_TYPE} to a {@link Comparator} implementation.
     */
    private static final Map<SORT_TYPE, Comparator<MessageInfoHolder>> SORT_COMPARATORS;

    static {
        // fill the mapping at class time loading

        final Map<SORT_TYPE, Comparator<MessageInfoHolder>> map = new EnumMap<SORT_TYPE, Comparator<MessageInfoHolder>>(SORT_TYPE.class);
        map.put(SORT_TYPE.SORT_ATTACHMENT, new AttachmentComparator());
        map.put(SORT_TYPE.SORT_DATE, new DateComparator());
        map.put(SORT_TYPE.SORT_FLAGGED, new FlaggedComparator());
        map.put(SORT_TYPE.SORT_SENDER, new SenderComparator());
        map.put(SORT_TYPE.SORT_SUBJECT, new SubjectComparator());
        map.put(SORT_TYPE.SORT_UNREAD, new UnreadComparator());

        // make it immutable to prevent accidental alteration (content is immutable already)
        SORT_COMPARATORS = Collections.unmodifiableMap(map);
    }

    private ListView mListView;



    private boolean mTouchView = true;
    private int mPreviewLines = 0;


    private MessageListAdapter mAdapter;
    private View mFooterView;

    private FolderInfoHolder mCurrentFolder;

    private LayoutInflater mInflater;

    private MessagingController mController;

    private Account mAccount;
    private int mUnreadMessageCount = 0;

    private GestureDetector gestureDetector;
    private View.OnTouchListener gestureListener;
    /**
     * Stores the name of the folder that we want to open as soon as possible
     * after load.
     */
    private String mFolderName;

    /**
     * If we're doing a search, this contains the query string.
     */
    private String mQueryString;
    private Flag[] mQueryFlags = null;
    private Flag[] mForbiddenFlags = null;
    private boolean mIntegrate = false;
    private String[] mAccountUuids = null;
    private String[] mFolderNames = null;
    private String mTitle;

    private MessageListHandler mHandler = new MessageListHandler();

    /*
     * If set, go to this folder when the back button is pressed
     */
    private String mGotoFolderOnBack;

    private SORT_TYPE sortType = SORT_TYPE.SORT_DATE;

    private boolean sortAscending = true;
    private boolean sortDateAscending = false;

    private boolean mStars = true;
    private boolean mCheckboxes = true;
    private int mSelectedCount = 0;

    private FontSizes mFontSizes = K9.getFontSizes();

    private Bundle mState = null;

    /**
     * Remember the selection to be consistent between menu display and menu item
     * selection
     */
    private MessageInfoHolder mSelectedMessage;

    /**
     * Relevant messages for the current context when we have to remember the
     * chosen messages between user interactions (eg. Selecting a folder for
     * move operation)
     */
    private List<MessageInfoHolder> mActiveMessages;

    private Context context;

    /* package visibility for faster inner class access */
    MessageHelper mMessageHelper = MessageHelper.getInstance(this);

    /** this variable is used to save the calling AttachmentView
     *  until the onActivityResult is called.
     *  => with this reference we can identity the caller
     */
    private AttachmentView attachmentTmpStore;


    private StorageManager.StorageListener mStorageListener = new StorageListenerImplementation();

    private final class StorageListenerImplementation implements StorageManager.StorageListener {
        @Override
        public void onUnmount(String providerId) {
            if (mAccount != null && providerId.equals(mAccount.getLocalStorageProviderId())) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onAccountUnavailable();
                    }
                });
            }
        }

        @Override
        public void onMount(String providerId) {
            // no-op
        }
    }

    class MessageListHandler extends Handler {
        /**
         * @param messages Never {@code null}.
         */
        public void removeMessages(final List<MessageInfoHolder> messages) {
            if (messages.isEmpty()) {
                return;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (MessageInfoHolder message : messages) {
                        if (message != null) {
                            if (mFolderName == null || (message.folder != null && message.folder.name.equals(mFolderName))) {
                                if (message.selected && mSelectedCount > 0) {
                                    mSelectedCount--;
                                }
                                mAdapter.messages.remove(message);
                            }
                        }
                    }
                    resetUnreadCountOnThread();

                    mAdapter.notifyDataSetChanged();
                    toggleBatchMode();
                }
            });
        }

        /**
         * @param messages Never {@code null}.
         */
        public void addMessages(final List<MessageInfoHolder> messages) {
            if (messages.isEmpty()) {
                return;
            }
            final boolean wasEmpty = mAdapter.messages.isEmpty();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (final MessageInfoHolder message : messages) {
                        if (mFolderName == null || (message.folder != null && message.folder.name.equals(mFolderName))) {
                            int index;
                            synchronized (mAdapter.messages) {
                                index = Collections.binarySearch(mAdapter.messages, message, getComparator());
                            }

                            if (index < 0) {
                                index = (index * -1) - 1;
                            }

                            mAdapter.messages.add(index, message);
                        }
                    }

                    if (wasEmpty) {
                        mListView.setSelection(0);
                        mListView.setItemChecked(0, true);
                    }
                    resetUnreadCountOnThread();

                    mAdapter.notifyDataSetChanged();
                }
            });
        }

        private void resetUnreadCount() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resetUnreadCountOnThread();
                }
            });
        }

        private void resetUnreadCountOnThread() {
            if (mQueryString != null) {
                int unreadCount = 0;
                synchronized (mAdapter.messages) {
                    for (MessageInfoHolder holder : mAdapter.messages) {
                        unreadCount += holder.read ? 0 : 1;
                    }
                }
                mUnreadMessageCount = unreadCount;
                refreshTitleOnThread();
            }
        }

        private void sortMessages() {
            final Comparator<MessageInfoHolder> chainComparator = getComparator();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    synchronized (mAdapter.messages) {
                        Collections.sort(mAdapter.messages, chainComparator);
                    }
                    mAdapter.notifyDataSetChanged();
                }
            });
        }

        /**
         * @return The comparator to use to display messages in an ordered
         *         fashion. Never <code>null</code>.
         */
        protected Comparator<MessageInfoHolder> getComparator() {
            final List<Comparator<MessageInfoHolder>> chain = new ArrayList<Comparator<MessageInfoHolder>>(2 /* we add 2 comparators at most */);

            {
                // add the specified comparator
                final Comparator<MessageInfoHolder> comparator = SORT_COMPARATORS.get(sortType);
                if (sortAscending) {
                    chain.add(comparator);
                } else {
                    chain.add(new ReverseComparator<MessageInfoHolder>(comparator));
                }
            }

            {
                // add the date comparator if not already specified
                if (sortType != SORT_TYPE.SORT_DATE) {
                    final Comparator<MessageInfoHolder> comparator = SORT_COMPARATORS.get(SORT_TYPE.SORT_DATE);
                    if (sortDateAscending) {
                        chain.add(comparator);
                    } else {
                        chain.add(new ReverseComparator<MessageInfoHolder>(comparator));
                    }
                }
            }

            // build the comparator chain
            final Comparator<MessageInfoHolder> chainComparator = new ComparatorChain<MessageInfoHolder>(chain);

            return chainComparator;
        }

        public void folderLoading(String folder, boolean loading) {
            if (mCurrentFolder != null && mCurrentFolder.name.equals(folder)) {
                mCurrentFolder.loading = loading;
            }
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    updateFooterView();
                }
            });
        }

        private void refreshTitle() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshTitleOnThread();
                }
            });
        }

        private void refreshTitleOnThread() {
            setActionBarTitle();
            setWindowProgress();
        }

        private void setWindowProgress() {
            int level = Window.PROGRESS_END;

            if (mCurrentFolder != null && mCurrentFolder.loading && mAdapter.mListener.getFolderTotal() > 0) {
                int divisor = mAdapter.mListener.getFolderTotal();
                if (divisor != 0) {
                    level = (Window.PROGRESS_END / divisor) * (mAdapter.mListener.getFolderCompleted()) ;
                    if (level > Window.PROGRESS_END) {
                        level = Window.PROGRESS_END;
                    }
                }
            }

            getWindow().setFeatureInt(Window.FEATURE_PROGRESS, level);
        }

        public void progress(final boolean progress) {
            runOnUiThread(new Runnable() {
                public void run() {
                    showProgressIndicator(progress);
                }
            });
        }



        public void addAttachment(final View attachmentView) {
            runOnUiThread(new Runnable() {
                public void run() {
                    mMessageView.addAttachment(attachmentView);
                }
            });
        }

        public void networkError() {
            showToast(getString(R.string.status_network_error), Toast.LENGTH_LONG);
        }

        public void invalidIdError() {
            showToast(getString(R.string.status_invalid_id_error), Toast.LENGTH_LONG);
        }


        public void fetchingAttachment() {
            showToast(getString(R.string.message_view_fetching_attachment_toast), Toast.LENGTH_SHORT);
        }


        public void setHeaders(final Message message, final Account account) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mMessageView.setHeaders(message, account);
                }
            });
        }

    }

    public static void actionHandleFolder(Context context, Account account, String folder) {
        Intent intent = actionHandleFolderIntent(context, account, folder);
        context.startActivity(intent);
    }

    public static Intent actionHandleFolderIntent(Context context, Account account, String folder) {
        Intent intent = new Intent(context, MessageList.class);
        intent.putExtra(EXTRA_ACCOUNT, account.getUuid());

        if (folder != null) {
            intent.putExtra(EXTRA_FOLDER, folder);
        }
        return intent;
    }

    public static void actionHandle(Context context, String title, String queryString, boolean integrate, Flag[] flags, Flag[] forbiddenFlags) {
        Intent intent = new Intent(context, MessageList.class);
        intent.putExtra(EXTRA_QUERY, queryString);
        if (flags != null) {
            intent.putExtra(EXTRA_QUERY_FLAGS, Utility.combine(flags, ','));
        }
        if (forbiddenFlags != null) {
            intent.putExtra(EXTRA_FORBIDDEN_FLAGS, Utility.combine(forbiddenFlags, ','));
        }
        intent.putExtra(EXTRA_INTEGRATE, integrate);
        intent.putExtra(EXTRA_TITLE, title);
        context.startActivity(intent);
    }

    public static void actionHandle(Context context, String title, SearchSpecification searchSpecification) {
        Intent intent = new Intent(context, MessageList.class);
        intent.putExtra(EXTRA_QUERY, searchSpecification.getQuery());
        if (searchSpecification.getRequiredFlags() != null) {
            intent.putExtra(EXTRA_QUERY_FLAGS, Utility.combine(searchSpecification.getRequiredFlags(), ','));
        }
        if (searchSpecification.getForbiddenFlags() != null) {
            intent.putExtra(EXTRA_FORBIDDEN_FLAGS, Utility.combine(searchSpecification.getForbiddenFlags(), ','));
        }
        intent.putExtra(EXTRA_INTEGRATE, searchSpecification.isIntegrate());
        intent.putExtra(EXTRA_ACCOUNT_UUIDS, searchSpecification.getAccountUuids());
        intent.putExtra(EXTRA_FOLDER_NAMES, searchSpecification.getFolderNames());
        intent.putExtra(EXTRA_TITLE, title);
        context.startActivity(intent);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (view == mFooterView) {
            if (mCurrentFolder != null) {
                mController.loadMoreMessages(mAccount, mFolderName, mAdapter.mListener);
            }
            return;
        }

        MessageInfoHolder message = (MessageInfoHolder) mAdapter.getItem(position);
        if (mSelectedCount > 0) {
            // In multiselect mode make sure that clicking on the item results
            // in toggling the 'selected' checkbox.
            setSelected(Collections.singletonList(message), !message.selected);
            if (!mSplitView.isPrimaryContentMaximized()) {
                onOpenMessage(message);
            }
        } else {
            onOpenMessage(message);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        context = this;
        super.onCreate(savedInstanceState);

        mInflater = getLayoutInflater();
        initializeLayout();
        onNewIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent); // onNewIntent doesn't autoset our "internal" intent

        // Only set "touchable" when we're first starting up the activity.
        // Otherwise we get force closes when the user toggles it midstream.
        mTouchView = K9.messageListTouchable();
        mPreviewLines = K9.messageListPreviewLines();

        String accountUuid = intent.getStringExtra(EXTRA_ACCOUNT);
        mAccount = Preferences.getPreferences(this).getAccount(accountUuid);
        mFolderName = intent.getStringExtra(EXTRA_FOLDER);
        mQueryString = intent.getStringExtra(EXTRA_QUERY);

        if (mAccount != null && !mAccount.isAvailable(this)) {
            Log.i(K9.LOG_TAG, "not opening MessageList of unavailable account");
            onAccountUnavailable();
            return;
        }

        String queryFlags = intent.getStringExtra(EXTRA_QUERY_FLAGS);
        if (queryFlags != null) {
            String[] flagStrings = queryFlags.split(",");
            mQueryFlags = new Flag[flagStrings.length];
            for (int i = 0; i < flagStrings.length; i++) {
                mQueryFlags[i] = Flag.valueOf(flagStrings[i]);
            }
        }
        String forbiddenFlags = intent.getStringExtra(EXTRA_FORBIDDEN_FLAGS);
        if (forbiddenFlags != null) {
            String[] flagStrings = forbiddenFlags.split(",");
            mForbiddenFlags = new Flag[flagStrings.length];
            for (int i = 0; i < flagStrings.length; i++) {
                mForbiddenFlags[i] = Flag.valueOf(flagStrings[i]);
            }
        }
        mIntegrate = intent.getBooleanExtra(EXTRA_INTEGRATE, false);
        mAccountUuids = intent.getStringArrayExtra(EXTRA_ACCOUNT_UUIDS);
        mFolderNames = intent.getStringArrayExtra(EXTRA_FOLDER_NAMES);
        mTitle = intent.getStringExtra(EXTRA_TITLE);

        // Take the initial folder into account only if we are *not* restoring
        // the activity already.
        if (mFolderName == null && mQueryString == null) {
            mFolderName = mAccount.getAutoExpandFolderName();
        }

        mAdapter = new MessageListAdapter();
        restorePreviousData();

        if (mFolderName != null) {
            mCurrentFolder = mAdapter.getFolder(mFolderName, mAccount);
        }

        mController = MessagingController.getInstance(getApplication());
        mListView.setAdapter(mAdapter);

        mListView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, final int position, long id) {

                mListView.setItemChecked(position, true);
                mCurrentMessageInfo = (MessageInfoHolder) mAdapter.getItem(position);
                if (!mSplitView.isPrimaryContentMaximized()) {
                    onOpenMessage(mCurrentMessageInfo);
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}



        }



                                           );

        /// Loading up by a URL TODO!!!
        showMessageFromIntentURL(intent);

    }
    public void showMessageFromIntentURL(Intent intent) {
        //TODO
        Uri uri = intent.getData();
        if (uri != null) {
            List<String> segmentList = uri.getPathSegments();
            if (segmentList.size() != 3) {
                //TODO: Use ressource to externalize message
                showToast("Invalid intent uri: " + uri.toString(), Toast.LENGTH_LONG);
                return;
            }

            String accountId = segmentList.get(0);
            Collection<Account> accounts = Preferences.getPreferences(this).getAvailableAccounts();
            boolean found = false;
            for (Account account : accounts) {
                if (String.valueOf(account.getAccountNumber()).equals(accountId)) {
                    mAccount = account;
                    found = true;
                    break;
                }
            }
            if (!found) {
                //TODO: Use ressource to externalize message
                showToast("Invalid account id: " + accountId, Toast.LENGTH_LONG);
                return;
            }

            MessageReference messageReference = new MessageReference();
            messageReference.accountUuid = mAccount.getUuid();
            messageReference.folderName = segmentList.get(1);
            messageReference.uid = segmentList.get(2);
        }
    }

    private void restorePreviousData() {
        final ActivityState previousData = getLastNonConfigurationInstance();

        if (previousData != null) {
            mAdapter.messages.addAll(previousData.messages);
            mActiveMessages = previousData.activeMessages;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mController.removeListener(mAdapter.mListener);
        saveListState();

        StorageManager.getInstance(getApplication()).removeListener(mStorageListener);
    }

    public void saveListState() {
        mState = new Bundle();
        mState.putInt(EXTRA_LIST_POSITION, mListView.getSelectedItemPosition());
        mState.putSerializable(STATE_PGP_DATA, mPgpData);
        mState.putBoolean(SHOW_PICTURES, mMessageView.showPictures());
    }

    public void restoreListState() {
        if (mState == null) {
            return;
        }


        mPgpData = (PgpData) mState.getSerializable(STATE_PGP_DATA);
        if (mAccount != null && mPgpData != null) {
            mMessageView.updateCryptoLayout(mAccount.getCryptoProvider(), mPgpData, mMessage);
        }
        mMessageView.setLoadPictures(mState.getBoolean(SHOW_PICTURES));


        int pos = mState.getInt(EXTRA_LIST_POSITION, ListView.INVALID_POSITION);

        if (pos >= mListView.getCount()) {
            pos = mListView.getCount() - 1;
        }

        if (pos == ListView.INVALID_POSITION) {
            mListView.setSelected(false);
        } else {
            mListView.setSelection(pos);
            mListView.setItemChecked(pos, true);

            // smoothScrollToPosition came in with API level 8
            if (Integer.parseInt(Build.VERSION.SDK) >= Build.VERSION_CODES.FROYO) {
                mListView.smoothScrollToPosition(pos);
            }
            onOpenMessage((MessageInfoHolder) mAdapter.getItem(pos));
        }
    }

    /**
     * On resume we refresh messages for the folder that is currently open.
     * This guarantees that things like unread message count and read status
     * are updated.
     */
    @Override
    public void onResume() {
        super.onResume();

        if (mAccount != null && !mAccount.isAvailable(this)) {
            onAccountUnavailable();
            return;
        }
        StorageManager.getInstance(getApplication()).addListener(mStorageListener);

        mStars = K9.messageListStars();
        mCheckboxes = K9.messageListCheckboxes();

        sortType = mController.getSortType();
        sortAscending = mController.isSortAscending(sortType);
        sortDateAscending = mController.isSortAscending(SORT_TYPE.SORT_DATE);

        mController.addListener(mAdapter.mListener);
        if (mAccount != null) {
            mController.notifyAccountCancel(this, mAccount);
            MessagingController.getInstance(getApplication()).notifyAccountCancel(this, mAccount);
        }

        if (mAdapter.messages.isEmpty()) {
            if (mFolderName != null) {
                mController.listLocalMessages(mAccount, mFolderName,  mAdapter.mListener);
            } else if (mQueryString != null) {
                mController.searchLocalMessages(mAccountUuids, mFolderNames, null, mQueryString, mIntegrate, mQueryFlags, mForbiddenFlags, mAdapter.mListener);
            }

        } else {
            // reread the selected date format preference in case it has changed
            mMessageHelper.refresh();

            new Thread() {
                @Override
                public void run() {
                    mAdapter.markAllMessagesAsDirty();

                    if (mFolderName != null) {
                        mController.listLocalMessagesSynchronous(mAccount, mFolderName,  mAdapter.mListener);
                    } else if (mQueryString != null) {
                        mController.searchLocalMessagesSynchronous(mAccountUuids, mFolderNames, null, mQueryString, mIntegrate, mQueryFlags, mForbiddenFlags, mAdapter.mListener);
                    }


                    mAdapter.pruneDirtyMessages();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.notifyDataSetChanged();
                            restoreListState();
                        }
                    });
                }

            }
            .start();
        }

        if (mAccount != null && mFolderName != null) {
            mController.getFolderUnreadMessageCount(mAccount, mFolderName, mAdapter.mListener);
        }
    }
    private void initializeLayout() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        float aspectRatio = getAspectRatio();
        // If we have a large, square screen, then move around the
        // button bar to be somewhere more useful
        if (getScreenSizeInInches() > K9.TABLET_MIN_SIZE
                && (1.34 > aspectRatio && aspectRatio >= 0.75)) {

            setContentView(R.layout.message_list_square_device);

        } else {
            setContentView(R.layout.message_list);
        }
        initializeActionBar();

        mSplitView = (SplitView) findViewById(R.id.splitview);


        setInitialListViewSize();

        mListView = (ListView) findViewById(R.id.message_list);
        mListView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        mListView.setLongClickable(true);
        mListView.setFastScrollEnabled(true);
        mListView.setScrollingCacheEnabled(true);
        mListView.setFocusableInTouchMode(true);
        mListView.setOnItemClickListener(this);
        mListView.addFooterView(getFooterView(mListView));


        mListView.setItemsCanFocus(false);
        mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);



        registerForContextMenu(mListView);


        initializeMessageView();
        setupButtonViews();

        // Gesture detection
        gestureDetector = new GestureDetector(new MyGestureDetector());
        gestureListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (gestureDetector.onTouchEvent(event)) {
                    return true;
                }
                return false;
            }
        };

        mListView.setOnTouchListener(gestureListener);


    }

    private void initializeActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.removeAllActions();
        actionBar.clearHomeAction();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.addAction(new AbstractAction(R.drawable.ic_actionbar_search) {
            @Override public void performAction(View view) {
                onSearchRequested();
            }
        });
        actionBar.addAction(new AbstractAction(R.drawable.ic_actionbar_refresh) {
            @Override public void performAction(View view) {
                checkMail(mAccount, mFolderName);
            }
        });
        actionBar.addAction(new AbstractAction(R.drawable.ic_actionbar_compose) {
            @Override public void performAction(View view) {
                showComposeMenu(view);
            }
        });
        actionBar.setOnTitleClickListener(
        new OnClickListener() {
            @Override public void onClick(View v) {
                if (mSplitView.isSecondaryContentMaximized()) {
                    mSplitView.maximizePrimaryContent();
                    return;
                }  else {
                    onShowFolderList();
                }
            }
        });

    }

    private void initializeBatchActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.removeAllActions();

        actionBar.addAction(new AbstractAction(R.drawable.ic_actionbar_delete) {
            @Override public void performAction(View view) {
                final List<MessageInfoHolder> selection = getSelectionFromCheckboxes();
                onDelete(selection);
            }
        });

        final boolean newReadState = computeBatchDirection(false);
        actionBar.addAction(new AbstractAction(newReadState ? R.drawable.ic_actionbar_mark_read : R.drawable.ic_actionbar_mark_unread) {
            @Override public void performAction(View view) {
                final List<MessageInfoHolder> selection = getSelectionFromCheckboxes();
                setFlag(selection, Flag.SEEN, newReadState);
            }


        });

        final boolean newFlagState = computeBatchDirection(true);
        actionBar.addAction(new AbstractAction(newFlagState ? R.drawable.ic_actionbar_flag : R.drawable.ic_actionbar_unflag) {
            @Override public void performAction(View view) {
                final List<MessageInfoHolder> selection = getSelectionFromCheckboxes();
                setFlag(selection, Flag.FLAGGED, newReadState);
            }


        });

        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setHomeAction(new AbstractAction(R.drawable.ic_actionbar_cancel) {
            @Override public void performAction(View view) {
                setAllSelected(false);
                toggleBatchMode();
            }
        });
        actionBar.setOnTitleClickListener(null);
    }

    private void setInitialListViewSize() {


        if (getScreenSizeInInches() < K9.TABLET_MIN_SIZE) {
            mSplitView.maximizePrimaryContent();
        } else {
            mSplitView.setPrimaryContentSize(K9.getPrimaryMessageListContentSize());
        }
    }


    public void setActionBarTitle() {
        ActionBar actionBar = getActionBar();
        String titleString = "";
        if (mSelectedCount > 0)  {
            actionBar.setTitle(getResources().getQuantityString(R.plurals.selected_message_count, mSelectedCount, mSelectedCount));

            return;
        }

        if (mAccount != null && mFolderName != null) {
            String displayName  = mFolderName;
            if (mAccount.getInboxFolderName().equalsIgnoreCase(displayName)) {
                displayName = getString(R.string.special_mailbox_name_inbox);
            } else if (mAccount.getOutboxFolderName().equals(displayName)) {
                displayName = getString(R.string.special_mailbox_name_outbox);
            }

            titleString = mAccount.getDescription() + " / " + displayName;
        } else if (mQueryString != null) {
            titleString = getString(R.string.search_results) + " / " + mQueryString;
        }
        actionBar.setTitle(mAdapter.mListener.formatHeader(MessageList.this, mAccount, titleString, mUnreadMessageCount, getTimeFormat()));
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_UP) {
            // Text selection is finished. Allow scrolling again.
            mToggleScrollView.setScrolling(true);
        } else if (K9.zoomControlsEnabled()) {
            // If we have system zoom controls enabled, disable scrolling so the screen isn't wiggling around while
            // trying to zoom.
            if (ev.getAction() == MotionEvent.ACTION_POINTER_2_DOWN) {
                mToggleScrollView.setScrolling(false);
            } else if (ev.getAction() == MotionEvent.ACTION_POINTER_2_UP) {
                mToggleScrollView.setScrolling(true);
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void initializeMessageView() {

        mTopView = mToggleScrollView = (ToggleScrollView) findViewById(R.id.top_view);

        mMessageView = (SingleMessageView) findViewById(R.id.message_view);
        //set a callback for the attachment view. With this callback the attachmentview
        //request the start of a filebrowser activity.
        mMessageView.setAttachmentCallback(new AttachmentFileDownloadCallback() {

            @Override
            public void showFileBrowser(final AttachmentView caller) {
                FileBrowserHelper.getInstance()
                .showFileBrowserActivity(MessageList.this, null, MessageList.ACTIVITY_CHOOSE_DIRECTORY, callback);
                attachmentTmpStore = caller;
            }
            FileBrowserFailOverCallback callback = new FileBrowserFailOverCallback() {

                @Override
                public void onPathEntered(String path) {
                    attachmentTmpStore.writeFile(new File(path));
                }

                // canceled, do nothing
                @Override public void onCancel() { }
            };
        });


        mMessageView.initialize(this);

    }

    /**
     * Container for values to be kept while the device configuration is
     * modified at runtime (keyboard, orientation, etc.) and Android restarts
     * this activity.
     *
     * @see MessageList#onRetainNonConfigurationInstance()
     * @see MessageList#getLastNonConfigurationInstance()
     */
    static class ActivityState {
        public List<MessageInfoHolder> messages;
        public List<MessageInfoHolder> activeMessages;
    }

    /* (non-Javadoc)
     *
     * Method overriden for proper typing within this class (the return type is
     * more specific than the super implementation)
     *
     * @see android.app.Activity#onRetainNonConfigurationInstance()
     */
    @Override
    public ActivityState onRetainNonConfigurationInstance() {
        final ActivityState state = new ActivityState();
        state.messages = mAdapter.messages;
        state.activeMessages = mActiveMessages;
        return state;
    }

    /*
     * (non-Javadoc)
     *
     * Method overriden for proper typing within this class (the return type is
     * more specific than the super implementation)
     *
     * @see android.app.Activity#getLastNonConfigurationInstance()
     */
    @Override
    public ActivityState getLastNonConfigurationInstance() {
        return (ActivityState) super.getLastNonConfigurationInstance();
    }

    private void displayMessage(MessageInfoHolder holder) {
        mCurrentMessageInfo = holder;
        mAccount = Preferences.getPreferences(this).getAccount(mCurrentMessageInfo.accountUuid);
        clearMessageDisplay();
        findSurroundingMessagesUid();
        // start with fresh, empty PGP data
        mPgpData = new PgpData();
        mTopView.setVisibility(View.VISIBLE);
        mController.loadMessageForView(mAccount, holder.folder.name, holder.uid, mAdapter.mListener);
        setupDisplayMessageButtons();
    }


    public void displayMessageBody(final Account account, final String folder, final String uid, final Message message) {
        runOnUiThread(new Runnable() {
            public void run() {
                mTopView.scrollTo(0, 0);
                try {
                    if (MessageList.this.mMessage != null
                            && MessageList.this.mMessage.isSet(Flag.X_DOWNLOADED_PARTIAL)
                    && message.isSet(Flag.X_DOWNLOADED_FULL)) {
                        mMessageView.setHeaders(message, account);
                    }
                    MessageList.this.mMessage = message;
                    mMessageView.displayMessageBody(account, folder, uid, message, mPgpData);
                    mMessageView.renderAttachments(mMessage, 0, mMessage, mAccount, mController, mAdapter.mListener);
                } catch (MessagingException e) {
                    if (Config.LOGV) {
                        Log.v(K9.LOG_TAG, "loadMessageForViewBodyAvailable", e);
                    }
                }
            }
        });
    }


    /* A helper for a set of "show a toast" methods */
    private void showToast(final String message, final int toastLength)  {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MessageList.this, message, toastLength).show();
            }
        });
    }

    private void clearMessageDisplay() {
        mTopView.setVisibility(View.GONE);
        mTopView.scrollTo(0, 0);
        mMessageView.resetView();

    }

    @Override
    public void onBackPressed() {
        // This will be called either automatically for you on 2.0
        // or later, or by the code above on earlier versions of the
        // platform.
        //
        if (mSplitView.isSecondaryContentMaximized()) {
            setInitialListViewSize();
            return;
        } else {
            if (!mSplitView.isPrimaryContentMaximized()) {
                saveListViewSize();
            }
            if (K9.manageBack()) {
                if (mGotoFolderOnBack != null) {
                    actionHandleFolder(this,mAccount,mGotoFolderOnBack);
                    mGotoFolderOnBack = null;
                } else if (mQueryString == null) {
                    onShowFolderList();
                } else {
                    onAccounts();
                }
            } else {
                finish();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, final KeyEvent event) {
        if (
            // XXX TODO - when we go to android 2.0, uncomment this
            // android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ECLAIR &&
            keyCode == KeyEvent.KEYCODE_BACK
            && event.getRepeatCount() == 0
        ) {
            // Take care of calling this method on earlier versions of
            // the platform where it doesn't exist.
            onBackPressed();
            return true;
        }

        // Shortcuts that work no matter what is selected
        switch (keyCode) {

            // messagelist is actually a K9Activity, not a K9ListActivity
            // This saddens me greatly, but to support volume key navigation
            // in MessageView, we implement this bit of wrapper code
        case KeyEvent.KEYCODE_VOLUME_UP: {
            if (K9.useVolumeKeysForListNavigationEnabled()) {
                gotoPreviousItem();
                return true;
            }
            return false;
        }
        case KeyEvent.KEYCODE_VOLUME_DOWN: {
            if (K9.useVolumeKeysForListNavigationEnabled()) {

                gotoNextItem();
                return true;
            }
            return false;
        }
        case KeyEvent.KEYCODE_C: {
            onCompose();
            return true;
        }
        case KeyEvent.KEYCODE_Q: {
            onShowFolderList();
            return true;
        }
        case KeyEvent.KEYCODE_O: {
            onCycleSort();
            return true;
        }
        case KeyEvent.KEYCODE_I: {
            onToggleSortAscending();
            return true;
        }
        case KeyEvent.KEYCODE_5: {
            checkMail(mAccount, mFolderName);
            return true;
        }
        case KeyEvent.KEYCODE_SLASH: {
            onSearchRequested();
            return true;
        }
        case KeyEvent.KEYCODE_GRAVE: {
            if (mFolderName != null) {
                mGotoFolderOnBack = mFolderName;
                onSearchUnread();
                return true;
            }
        }
        case KeyEvent.KEYCODE_H: {
            showToast(getString(R.string.message_list_help_key), Toast.LENGTH_LONG);
            return true;
        }
        }

        boolean retval = true;
        try {
            if (mCurrentMessageInfo != null) {
                MessageInfoHolder message = mCurrentMessageInfo;

                final List<MessageInfoHolder> selection = getSelectionFromMessage(mCurrentMessageInfo);

                if (message != null) {
                    switch (keyCode) {
                    case KeyEvent.KEYCODE_D:
                    case KeyEvent.KEYCODE_DEL: {
                        onDelete(selection);
                        return true;
                    }
                    case KeyEvent.KEYCODE_S: {
                        setSelected(selection, !message.selected);
                        return true;
                    }
                    case KeyEvent.KEYCODE_F: {
                        onForward(message);
                        return true;
                    }
                    case KeyEvent.KEYCODE_A: {
                        onReplyAll(message);
                        return true;
                    }
                    case KeyEvent.KEYCODE_R: {
                        onReply(message);
                        return true;
                    }
                    case KeyEvent.KEYCODE_G: {
                        setFlag(selection, Flag.FLAGGED, !message.flagged);
                        return true;
                    }
                    case KeyEvent.KEYCODE_M: {
                        onMove(selection);
                        return true;
                    }
                    case KeyEvent.KEYCODE_V: {
                        onArchive(selection);
                        return true;
                    }
                    case KeyEvent.KEYCODE_Y: {
                        onCopy(selection);
                        return true;
                    }
                    case KeyEvent.KEYCODE_Z: {
                        setFlag(selection, Flag.SEEN, !message.read);
                        return true;
                    }
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                    case KeyEvent.KEYCODE_J:
                    case KeyEvent.KEYCODE_P: {
                        gotoPreviousItem();
                        return true;
                    }
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_N:
                    case KeyEvent.KEYCODE_K: {
                        gotoNextItem();
                        return true;
                    }
                    /* TODO - MESSAGE VIEW SPECIAL KEYS
                    case KeyEvent.KEYCODE_SHIFT_LEFT:
                    case KeyEvent.KEYCODE_SHIFT_RIGHT: {
                        // Selecting text started via shift key. Disable scrolling as
                        // this causes problems when selecting text.
                        mToggleScrollView.setScrolling(false);
                        break;
                    }
                    case KeyEvent.KEYCODE_S: {
                        onRefile(mAccount.getSpamFolderName());
                        return true;
                    }
                    case KeyEvent.KEYCODE_Z: {
                        mHandler.post(new Runnable() {
                            public void run() {
                                mMessageView.zoom(event);
                            }
                        });
                        return true;
                    }
                        */
                    case KeyEvent.KEYCODE_H: {
                        showToast(getString(R.string.message_help_key), Toast.LENGTH_LONG);
                        return true;
                    }
                    }
                }
            }
        } finally {
            retval = super.onKeyDown(keyCode, event);
        }
        return retval;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Swallow these events too to avoid the audible notification of a volume change
        if (K9.useVolumeKeysForListNavigationEnabled()) {
            if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) || (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
                if (K9.DEBUG)
                    Log.v(K9.LOG_TAG, "Swallowed key up.");
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    private void findSurroundingMessagesUid() {
        mNextMessage = mPreviousMessage = null;
        int i = mAdapter.messages.indexOf(mCurrentMessageInfo);
        if (i < 0)
            i = 0;
        if (i != 0)
            mNextMessage = mAdapter.messages.get(i - 1);
        if (i < (mAdapter.messages.size() - 1))
            mPreviousMessage = mAdapter.messages.get(i + 1);
    }

    private void showNextMessageOrReturn() {
        if (K9.messageViewReturnToList() &&
                mSplitView.isSecondaryContentMaximized()) {
            setInitialListViewSize();
        } else {
            showNextMessage();
        }
    }

    private void showNextMessage() {
        if (mLastDirection == NEXT && mNextMessage != null) {
            gotoNextItem();
        } else if (mLastDirection == PREVIOUS && mPreviousMessage != null) {
            gotoPreviousItem();
        } else if (mNextMessage != null) {
            gotoNextItem();
        } else if (mPreviousMessage != null) {
            gotoPreviousItem();
        } else {
            finish();
        }
    }


    private void gotoNextItem() {
        if (mNextMessage == null) {
            showToast(getString(R.string.end_of_folder), Toast.LENGTH_SHORT);
            return;
        }
        mLastDirection = NEXT;
        mListView.setSelection(mAdapter.messages.indexOf(mNextMessage));

        if (!mSplitView.isPrimaryContentMaximized()) {
            onOpenMessage(mNextMessage);
        }
    }

    private void gotoPreviousItem() {
        if (mPreviousMessage == null) {
            showToast(getString(R.string.end_of_folder), Toast.LENGTH_SHORT);
            return;
        }
        mLastDirection = PREVIOUS;
        mListView.setSelection(mAdapter.messages.indexOf(mPreviousMessage));
        if (!mSplitView.isPrimaryContentMaximized()) {
            onOpenMessage(mPreviousMessage);
        }
    }


    private void onResendMessage(MessageInfoHolder message) {
        MessageCompose.actionEditDraft(this, message.message.getFolder().getAccount(), message.message);
    }

    private void onOpenMessage(final MessageInfoHolder message) {
        if (message == null) {
            return;
        }
        mHandler.post(new Runnable() {
            public void run() {
                if (mSplitView.isPrimaryContentMaximized()) {
                    if (getScreenSizeInInches() < K9.TABLET_MIN_SIZE) {
                        mSplitView.maximizeSecondaryContent();
                    } else {
                        mSplitView.setPrimaryContentSize(K9.getPrimaryMessageListContentSize());
                    }
                }
                displayMessage(message);
            }
        });

        /*
         * We set read=true here for UI performance reasons. The actual value
         * will get picked up on the refresh when the Activity is resumed but
         * that may take a second or so and we don't want this to show and
         * then go away. I've gone back and forth on this, and this gives a
         * better UI experience, so I am putting it back in.
         */
        if (!message.read) {
            message.read = true;
        }
    }
    private void onDownloadRemainder() {
        if (mMessage.isSet(Flag.X_DOWNLOADED_FULL)) {
            return;
        }
        mMessageView.downloadRemainderButton().setEnabled(false);
        mController.loadMessageForViewRemote(mAccount, mCurrentMessageInfo.folder.name, mCurrentMessageInfo.uid, mAdapter.mListener);
    }

    private void onAccounts() {
        Accounts.listAccounts(this);
        finish();
    }

    private void onShowFolderList() {
        FolderList.actionHandleAccount(this, mAccount);
        finish();
    }

    private void onCompose() {
        if (mQueryString != null) {
            /*
             * If we have a query string, we don't have an account to let
             * compose start the default action.
             */
            MessageCompose.actionCompose(this, null);
        } else {
            MessageCompose.actionCompose(this, mAccount);
        }
    }

    private void onEditPrefs() {
        Prefs.actionPrefs(this);
    }

    private void onEditAccount() {
        AccountSettings.actionSettings(this, mAccount);
    }

    private void changeSort(SORT_TYPE newSortType) {
        if (sortType == newSortType) {
            onToggleSortAscending();
        } else {
            sortType = newSortType;
            mController.setSortType(sortType);
            sortAscending = mController.isSortAscending(sortType);
            sortDateAscending = mController.isSortAscending(SORT_TYPE.SORT_DATE);
            reSort();
        }
    }

    private void reSort() {

        showToast(getString(sortType.getToast(sortAscending)), Toast.LENGTH_SHORT);

        mHandler.sortMessages();
    }

    private void onCycleSort() {
        SORT_TYPE[] sorts = SORT_TYPE.values();
        int curIndex = 0;

        for (int i = 0; i < sorts.length; i++) {
            if (sorts[i] == sortType) {
                curIndex = i;
                break;
            }
        }

        curIndex++;

        if (curIndex == sorts.length) {
            curIndex = 0;
        }

        changeSort(sorts[curIndex]);
    }

    private void onToggleSortAscending() {
        mController.setSortAscending(sortType, !sortAscending);

        sortAscending = mController.isSortAscending(sortType);
        sortDateAscending = mController.isSortAscending(SORT_TYPE.SORT_DATE);

        reSort();
    }

    public void  showComposeMenu(View view) {

        QuickAction qa = new QuickAction(view);
        ActionItem composeAction = new ActionItem();
        composeAction.setTitle(getString(R.string.compose_action));
        composeAction.setIcon(getResources().getDrawable(R.drawable.ic_menu_compose));
        composeAction.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                onCompose();
            }
        });
        qa.addActionItem(composeAction);

        if (mCurrentMessageInfo != null) {

            if (mCurrentMessageInfo.folder.name.equals(mCurrentMessageInfo.message.getFolder().getAccount().getDraftsFolderName())) {


                ActionItem editDraftAction = new ActionItem();
                editDraftAction.setTitle(getString(R.string.edit_draft_action));
                editDraftAction.setIcon(getResources().getDrawable(R.drawable.ic_menu_edit));
                editDraftAction.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        MessageCompose.actionEditDraft(MessageList.this, mAccount, mCurrentMessageInfo.message);
                    }
                });
                qa.addActionItem(editDraftAction);




            } else {
                ActionItem replyAction = new ActionItem();
                replyAction.setTitle(getString(R.string.reply_action));
                replyAction.setIcon(getResources().getDrawable(R.drawable.ic_menu_reply));
                replyAction.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        onReply(mCurrentMessageInfo);
                    }
                });
                qa.addActionItem(replyAction);
                ActionItem ReplyAllAction = new ActionItem();
                ReplyAllAction.setTitle(getString(R.string.reply_all_action));
                ReplyAllAction.setIcon(getResources().getDrawable(R.drawable.ic_menu_reply_all));
                ReplyAllAction.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        onReplyAll(mCurrentMessageInfo);
                    }
                });
                qa.addActionItem(ReplyAllAction);
                ActionItem forwardAction = new ActionItem();
                forwardAction.setTitle(getString(R.string.forward_action));
                forwardAction.setIcon(getResources().getDrawable(R.drawable.ic_menu_forward_mail));
                forwardAction.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        onForward(mCurrentMessageInfo);
                    }
                });
                qa.addActionItem(forwardAction);

                ActionItem shareAction = new ActionItem();
                shareAction.setTitle(getString(R.string.send_alternate_action));
                shareAction.setIcon(getResources().getDrawable(R.drawable.ic_menu_share));
                shareAction.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        onSendAlternate(mAccount, mCurrentMessageInfo);
                    }
                });
                qa.addActionItem(shareAction);




            }

        }


        qa.show();
    }

    // TODO - refactor to use this

    /**
     * @param holders
     *            Never {@code null}.
     */
    private void onDelete(final List<MessageInfoHolder> holders) {
        final List<Message> messagesToRemove = new ArrayList<Message>();
        for (MessageInfoHolder holder : holders) {
            // only change the current message being viewed if the one deleted was the current one
            if (mCurrentMessageInfo != null && holder.uid == mCurrentMessageInfo.uid) {
                showNextMessageOrReturn();
            }
            messagesToRemove.add(holder.message);
        }

        mHandler.removeMessages(holders);
        mController.deleteMessages(messagesToRemove.toArray(EMPTY_MESSAGE_ARRAY), null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mAccount.getCryptoProvider().onActivityResult(this, requestCode, resultCode, data, mPgpData)) {
            return;
        }

        if (resultCode != RESULT_OK) {
            return;
        }

        switch (requestCode) {
        case ACTIVITY_CHOOSE_FOLDER_MOVE:
        case ACTIVITY_CHOOSE_FOLDER_COPY: {
            if (data == null) {
                return;
            }

            final String destFolderName = data.getStringExtra(ChooseFolder.EXTRA_NEW_FOLDER);

            if (destFolderName != null) {
                final List<MessageInfoHolder> holders = mActiveMessages;

                mActiveMessages = null; // don't need it any more

                final Account account = holders.get(0).message.getFolder().getAccount();
                account.setLastSelectedFolderName(destFolderName);

                switch (requestCode) {
                case ACTIVITY_CHOOSE_FOLDER_MOVE:
                    move(holders, destFolderName);
                    // TODO showNextMessageOrReturn();
                    break;

                case ACTIVITY_CHOOSE_FOLDER_COPY:
                    copy(holders, destFolderName);
                    break;
                }
            }
            break;
        }
        case ACTIVITY_CHOOSE_DIRECTORY: {
            if (resultCode == RESULT_OK && data != null) {
                // obtain the filename
                Uri fileUri = data.getData();
                if (fileUri != null) {
                    String filePath = fileUri.getPath();
                    if (filePath != null) {
                        attachmentTmpStore.writeFile(new File(filePath));
                    }
                }
            }

            break;

        }
        }
    }



    private void onReply(MessageInfoHolder holder) {
        // TODO - need to re-enable pgp support here
        MessageCompose.actionReply(this, holder.message.getFolder().getAccount(), holder.message, false, null);
    }

    private void onReplyAll(MessageInfoHolder holder) {
        // TODO - need to re-enable pgp support here
        MessageCompose.actionReply(this, holder.message.getFolder().getAccount(), holder.message, true, null);
    }

    private void onForward(MessageInfoHolder holder) {
        // TODO - need to re-enable pgp support here
        MessageCompose.actionForward(this, holder.message.getFolder().getAccount(), holder.message, null);
    }

    private void onMarkAllAsRead(final Account account, final String folder) {
        if (K9.confirmMarkAllAsRead()) {
            showDialog(DIALOG_MARK_ALL_AS_READ);
        } else {
            markAllAsRead();
        }
    }

    private void markAllAsRead() {
        try {
            mController.markAllMessagesRead(mAccount, mCurrentFolder.name);

            synchronized (mAdapter.messages) {
                for (MessageInfoHolder holder : mAdapter.messages) {
                    holder.read = true;
                }
            }
            mHandler.sortMessages();
        } catch (Exception e) {
            // Ignore
        }
    }


    // TODO: when switching to API version 8, override onCreateDialog(int, Bundle)

    /**
     * @param id The id of the dialog.
     * @return The dialog. If you return null, the dialog will not be created.
     * @see android.app.Activity#onCreateDialog(int)
     */
    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_MARK_ALL_AS_READ:
            return createMarkAllAsReadDialog();
        case R.id.dialog_confirm_delete:
            return createConfirmDeleteDialog(id);
        case R.id.dialog_confirm_spam:
            return ConfirmationDialog.create(this, id,
                                             R.string.dialog_confirm_spam_title,
                                             "" /* text is refreshed by #onPrepareDialog(int, Dialog) below */,
                                             R.string.dialog_confirm_spam_confirm_button,
                                             R.string.dialog_confirm_spam_cancel_button,
            new Runnable() {
                @Override
                public void run() {
                    onSpamConfirmed(mActiveMessages);
                    // No further need for this reference
                    mActiveMessages = null;
                }
            }, new Runnable() {
                @Override
                public void run() {
                    // event for cancel, we don't need this reference any more
                    mActiveMessages = null;
                }
            });
        }

        return super.onCreateDialog(id);
    }

    /*
     * (non-Javadoc)
     *
     * Android happens to invoke this method even if the given dialog is not
     * shown (eg. a dismissed dialog) as part of the automatic activity
     * reloading following a configuration change (orientation, keyboard,
     * locale, etc.).
     */
    @Override
    public void onPrepareDialog(final int id, final Dialog dialog) {
        switch (id) {
        case DIALOG_MARK_ALL_AS_READ: {
            if (mCurrentFolder != null) {
                ((AlertDialog)dialog).setMessage(getString(R.string.mark_all_as_read_dlg_instructions_fmt,
                                                 mCurrentFolder.displayName));
            }
            break;
        }
        case R.id.dialog_confirm_spam: {
            // mActiveMessages can be null if Android restarts the activity
            // while this dialog is not actually shown (but was displayed at
            // least once)
            if (mActiveMessages != null) {
                final int selectionSize = mActiveMessages.size();
                final String message;
                message = getResources().getQuantityString(R.plurals.dialog_confirm_spam_message, selectionSize,
                          Integer.valueOf(selectionSize));
                ((AlertDialog) dialog).setMessage(message);
            }
            break;
        }
        default: {
            super.onPrepareDialog(id, dialog);
        }
        }
    }

    private Dialog createMarkAllAsReadDialog() {
        return new AlertDialog.Builder(this)
               .setTitle(R.string.mark_all_as_read_dlg_title)
               .setMessage(getString(R.string.mark_all_as_read_dlg_instructions_fmt,
                                     mCurrentFolder.displayName))
        .setPositiveButton(R.string.okay_action, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dismissDialog(DIALOG_MARK_ALL_AS_READ);

                try {
                    mController.markAllMessagesRead(mAccount, mCurrentFolder.name);

                    synchronized (mAdapter.messages) {
                        for (MessageInfoHolder holder : mAdapter.messages) {
                            holder.read = true;
                        }
                    }
                    mHandler.sortMessages();
                } catch (Exception e) {
                    // Ignore
                }
            }
        })
        .setNegativeButton(R.string.cancel_action, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dismissDialog(DIALOG_MARK_ALL_AS_READ);
            }
        })
               .create();
    }


    /**
     * @param id
     * @return Never <code>null</code>
     */
    protected Dialog createConfirmDeleteDialog(final int id) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_confirm_delete_title);
        builder.setMessage(R.string.dialog_confirm_delete_message);
        builder.setPositiveButton(R.string.dialog_confirm_delete_confirm_button,
        new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dismissDialog(id);
                // TODO delete();
            }
        });
        builder.setNegativeButton(R.string.dialog_confirm_delete_cancel_button,
        new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dismissDialog(id);
            }
        });
        return builder.create();
    }

    private void onToggleRead(MessageInfoHolder holder) {
        if (holder == null) {
            return;
        }
        mController.setFlag(holder.message.getFolder().getAccount(), holder.message.getFolder().getName(), new String[] { holder.uid }, Flag.SEEN, !holder.read);
        try {
            holder.read = !holder.read;
            holder.message.setFlag(Flag.SEEN, holder.read);
            if (mCurrentMessageInfo.uid == holder.uid) {
                mCurrentMessageInfo = holder;
                mMessageView.setHeaders(holder.message, holder.message.getFolder().getAccount());
            }
        } catch (Exception e) {
            Log.e(K9.LOG_TAG, "Unable to unset SEEN flag on message", e);
        }
        mHandler.sortMessages();
    }

    private void onToggleFlag(MessageInfoHolder holder) {
        if (holder == null) {
            return;
        }
        mController.setFlag(holder.message.getFolder().getAccount(), holder.message.getFolder().getName(), new String[] { holder.uid }, Flag.FLAGGED, !holder.flagged);
        try {
            holder.flagged = !holder.flagged;
            holder.message.setFlag(Flag.FLAGGED, holder.flagged);
            if (mCurrentMessageInfo != null && holder.uid == mCurrentMessageInfo.uid) {
                mCurrentMessageInfo = holder;
                mMessageView.setHeaders(holder.message, holder.message.getFolder().getAccount());
            }

        } catch (MessagingException me) {
            Log.e(K9.LOG_TAG, "Could not set flag on local message", me);
        }
        mHandler.sortMessages();
    }

    private void onSearchUnread() {
        if (mAccount == null || mFolderName == null) {
            return;
        }

        String description = getString(R.string.search_title, mAccount.getDescription(), getString(R.string.unread_modifier));
        final Flag[] flags = new Flag[] { Flag.SEEN };
        final String[] folders = new String[] { mFolderName };

        SearchSpecification searchSpec = new SearchSpecification() {
            //interface has no override            @Override
            public String[] getAccountUuids() {
                return new String[] { mAccount.getUuid() };
            }

            //interface has no override            @Override
            public Flag[] getForbiddenFlags() {
                return flags;
            }

            //interface has no override            @Override
            public String getQuery() {
                return "";
            }

            @Override
            public Flag[] getRequiredFlags() {
                return null;
            }

            @Override
            public boolean isIntegrate() {
                return false;
            }

            @Override
            public String[] getFolderNames() {
                return folders;
            }

        };
        MessageList.actionHandle(context, description, searchSpec);
    }

    private void checkMail(Account account, String folderName) {
        mController.synchronizeMailbox(account, folderName, mAdapter.mListener, null);
        mController.sendPendingMessages(account, mAdapter.mListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final List<MessageInfoHolder> selection = getSelectionFromCheckboxes();
        int itemId = item.getItemId();
        switch (itemId) {
        case R.id.set_sort_date: {
            changeSort(SORT_TYPE.SORT_DATE);
            return true;
        }
        case R.id.set_sort_subject: {
            changeSort(SORT_TYPE.SORT_SUBJECT);
            return true;
        }
        case R.id.set_sort_sender: {
            changeSort(SORT_TYPE.SORT_SENDER);
            return true;
        }
        case R.id.set_sort_flag: {
            changeSort(SORT_TYPE.SORT_FLAGGED);
            return true;
        }
        case R.id.set_sort_unread: {
            changeSort(SORT_TYPE.SORT_UNREAD);
            return true;
        }
        case R.id.set_sort_attach: {
            changeSort(SORT_TYPE.SORT_ATTACHMENT);
            return true;
        }
        case R.id.select_all:
        case R.id.batch_select_all: {
            setAllSelected(true);
            toggleBatchMode();
            return true;
        }
        case R.id.batch_deselect_all: {
            setAllSelected(false);
            toggleBatchMode();
            return true;
        }
        case R.id.app_settings: {
            onEditPrefs();
            return true;
        }
        }

        if (mQueryString != null) {
            // None of the options after this point are "safe" for search results
            //TODO: This is not true for "unread" and "starred" searches in regular folders
            return false;
        }

        switch (itemId) {
        case R.id.mark_all_as_read: {
            if (mFolderName != null) {
                onMarkAllAsRead(mAccount, mFolderName);
            }
            return true;
        }
        case R.id.folder_settings: {
            if (mFolderName != null) {
                FolderSettings.actionSettings(this, mAccount, mFolderName);
            }
            return true;
        }
        case R.id.account_settings: {
            onEditAccount();
            return true;
        }
        case R.id.batch_copy_op: {
            onCopy(selection);
            return true;
        }
        case R.id.batch_archive_op: {
            onArchive(selection);
            return true;
        }
        case R.id.batch_spam_op: {
            onSpam(selection);
            return true;
        }
        case R.id.batch_move_op: {
            onMove(selection);
            return true;
        }
        case R.id.show_full_header:
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mMessageView.showAllHeaders();
                }
            });
            return true;
        case R.id.select_text:
            mToggleScrollView.setScrolling(false);
            mMessageView.beginSelectingText();
            return true;
        default: {
            return super.onOptionsItemSelected(item);
        }
        }
    }

    private final int[] batch_ops = { R.id.batch_copy_op,
                                      R.id.batch_archive_op, R.id.batch_spam_op, R.id.batch_move_op,
                                      R.id.batch_select_all, R.id.batch_deselect_all
                                    };

    private void setOpsState(Menu menu, boolean state, boolean enabled) {
        for (int id : batch_ops) {
            menu.findItem(id).setVisible(state);
            menu.findItem(id).setEnabled(enabled);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean anySelected = anySelected();

        menu.findItem(R.id.select_all).setVisible(! anySelected);
        menu.findItem(R.id.batch_ops).setVisible(anySelected);

        setOpsState(menu, true, anySelected);

        if (mQueryString != null) {
            menu.findItem(R.id.mark_all_as_read).setVisible(false);
            menu.findItem(R.id.batch_archive_op).setVisible(false);
            menu.findItem(R.id.batch_spam_op).setVisible(false);
            menu.findItem(R.id.batch_move_op).setVisible(false);
            menu.findItem(R.id.batch_copy_op).setVisible(false);
            menu.findItem(R.id.folder_settings).setVisible(false);
            menu.findItem(R.id.account_settings).setVisible(false);
        } else {
            if (K9.FOLDER_NONE.equalsIgnoreCase(mAccount.getArchiveFolderName())) {
                menu.findItem(R.id.batch_archive_op).setVisible(false);
            }
            if (K9.FOLDER_NONE.equalsIgnoreCase(mAccount.getSpamFolderName())) {
                menu.findItem(R.id.batch_spam_op).setVisible(false);
            }
        }

        boolean newFlagState = computeBatchDirection(true);
        boolean newReadState = computeBatchDirection(false);
        menu.findItem(R.id.batch_deselect_all).setVisible(anySelected);
        menu.findItem(R.id.batch_select_all).setEnabled(true);

        MenuItem additionalHeadersItem = menu.findItem(R.id.show_full_header);
        if (additionalHeadersItem != null) {
            additionalHeadersItem.setTitle(mMessageView.additionalHeadersVisible() ?
                                           R.string.hide_full_header_action : R.string.show_full_header_action);
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.message_list_option, menu);

        return true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        final MessageInfoHolder holder = mSelectedMessage == null ? (MessageInfoHolder) mAdapter.getItem(info.position) : mSelectedMessage;
        // don't need this anymore
        mSelectedMessage = null;

        final List<MessageInfoHolder> selection = getSelectionFromMessage(holder);
        switch (item.getItemId()) {
        case R.id.open: {
            onOpenMessage(holder);
            break;
        }
        case R.id.select: {
            setSelected(selection, true);
            break;
        }
        case R.id.deselect: {
            setSelected(selection, false);
            break;
        }
        case R.id.delete: {
            onDelete(selection);
            break;
        }
        case R.id.reply: {
            onReply(holder);
            break;
        }
        case R.id.reply_all: {
            onReplyAll(holder);
            break;
        }
        case R.id.forward: {
            onForward(holder);
            break;
        }
        case R.id.send_again: {
            onResendMessage(holder);
            break;

        }
        case R.id.mark_as_read: {
            onToggleRead(holder);
            break;
        }
        case R.id.flag: {
            onToggleFlag(holder);
            break;
        }
        case R.id.archive: {
            onArchive(selection);
            break;
        }
        case R.id.spam: {
            onSpam(selection);
            break;
        }
        case R.id.move: {
            onMove(selection);
            break;
        }
        case R.id.copy: {
            onCopy(selection);
            break;
        }
        case R.id.send_alternate: {
            onSendAlternate(mAccount, holder);
            break;
        }
        case R.id.same_sender: {
            MessageList.actionHandle(MessageList.this,
                                     "From " + holder.sender, holder.senderAddress, true,
                                     null, null);
            break;
        }
        }
        return super.onContextItemSelected(item);
    }

    public void onSendAlternate(Account account, MessageInfoHolder holder) {
        mController.sendAlternate(this, account, holder.message);
    }

    public void showProgressIndicator(boolean status) {
        ActionBar bar = getActionBar();
        if (bar == null) {
            return;
        }

        bar.setProgressBarVisibility(status ? View.VISIBLE : View.GONE);
    }

    class MyGestureDetector extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e2 == null || e1 == null)
                return true;

            float deltaX = e2.getX() - e1.getX(),
                  deltaY = e2.getY() - e1.getY();

            boolean movedAcross = (Math.abs(deltaX) > Math.abs(deltaY * 4));
            boolean steadyHand = (Math.abs(deltaX / deltaY) > 2);

            if (movedAcross && steadyHand) {
                boolean selected = (deltaX > 0);
                int position = mListView.pointToPosition((int)e1.getX(), (int)e1.getY());

                if (position != AdapterView.INVALID_POSITION) {
                    MessageInfoHolder msgInfoHolder = (MessageInfoHolder) mAdapter.getItem(position);

                    if (msgInfoHolder != null && msgInfoHolder.selected != selected) {
                        msgInfoHolder.selected = selected;
                        mSelectedCount += (selected ? 1 : -1);
                        mAdapter.notifyDataSetChanged();
                        toggleBatchMode();
                    }
                }
            }

            return false;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        MessageInfoHolder message = (MessageInfoHolder) mAdapter.getItem(info.position);
        // remember which message was originally selected, in case the list changes while the
        // dialog is up
        mSelectedMessage = message;

        if (message == null) {
            return;
        }

        getMenuInflater().inflate(R.menu.message_list_context, menu);

        menu.setHeaderTitle(message.message.getSubject());

        if (message.read) {
            menu.findItem(R.id.mark_as_read).setTitle(R.string.mark_as_unread_action);
        }

        if (message.flagged) {
            menu.findItem(R.id.flag).setTitle(R.string.unflag_action);
        }

        Account account = message.message.getFolder().getAccount();
        if (!mController.isCopyCapable(account)) {
            menu.findItem(R.id.copy).setVisible(false);
        }

        if (!mController.isMoveCapable(account)) {
            menu.findItem(R.id.move).setVisible(false);
            menu.findItem(R.id.archive).setVisible(false);
            menu.findItem(R.id.spam).setVisible(false);
        }

        if (K9.FOLDER_NONE.equalsIgnoreCase(account.getArchiveFolderName())) {
            menu.findItem(R.id.archive).setVisible(false);
        }
        if (K9.FOLDER_NONE.equalsIgnoreCase(account.getSpamFolderName())) {
            menu.findItem(R.id.spam).setVisible(false);
        }

        if (message.selected) {
            menu.findItem(R.id.select).setVisible(false);
            menu.findItem(R.id.deselect).setVisible(true);
        } else {
            menu.findItem(R.id.select).setVisible(true);
            menu.findItem(R.id.deselect).setVisible(false);
        }
    }

    class MessageListAdapter extends BaseAdapter {
        private final List<MessageInfoHolder> messages = java.util.Collections.synchronizedList(new ArrayList<MessageInfoHolder>());

        private final ActivityListener mListener = new ActivityListener() {

            @Override
            public void informUserOfStatus() {
                mHandler.refreshTitle();
            }

            @Override
            public void synchronizeMailboxStarted(Account account, String folder) {
                if (updateForMe(account, folder)) {
                    mHandler.progress(true);
                    mHandler.folderLoading(folder, true);
                }
                super.synchronizeMailboxStarted(account, folder);
            }

            @Override
            public void synchronizeMailboxFinished(Account account, String folder,
            int totalMessagesInMailbox, int numNewMessages) {

                if (updateForMe(account, folder)) {
                    mHandler.progress(false);
                    mHandler.folderLoading(folder, false);
                    mHandler.sortMessages();
                }
                super.synchronizeMailboxFinished(account, folder, totalMessagesInMailbox, numNewMessages);
            }

            @Override
            public void synchronizeMailboxFailed(Account account, String folder, String message) {

                if (updateForMe(account, folder)) {
                    mHandler.progress(false);
                    mHandler.folderLoading(folder, false);
                    mHandler.sortMessages();
                }
                super.synchronizeMailboxFailed(account, folder, message);
            }

            @Override
            public void synchronizeMailboxAddOrUpdateMessage(Account account, String folder, Message message) {
                addOrUpdateMessage(account, folder, message, true);
            }

            @Override
            public void synchronizeMailboxRemovedMessage(Account account, String folder, Message message) {
                MessageInfoHolder holder = getMessage(message);
                if (holder == null) {
                    Log.w(K9.LOG_TAG, "Got callback to remove non-existent message with UID " + message.getUid());
                } else {
                    removeMessages(Collections.singletonList(holder));
                }
            }

            @Override
            public void listLocalMessagesStarted(Account account, String folder) {
                if ((mQueryString != null && folder == null) || (account != null && account.equals(mAccount))) {
                    mHandler.progress(true);
                    if (folder != null) {
                        mHandler.folderLoading(folder, true);
                    }
                }
            }

            @Override
            public void listLocalMessagesFailed(Account account, String folder, String message) {
                if ((mQueryString != null && folder == null) || (account != null && account.equals(mAccount))) {
                    mHandler.sortMessages();
                    mHandler.progress(false);
                    if (folder != null) {
                        mHandler.folderLoading(folder, false);
                    }
                }
            }

            @Override
            public void listLocalMessagesFinished(Account account, String folder) {
                if ((mQueryString != null && folder == null) || (account != null && account.equals(mAccount))) {
                    mHandler.sortMessages();
                    mHandler.progress(false);
                    if (folder != null) {
                        mHandler.folderLoading(folder, false);
                    }
                }
            }

            @Override
            public void listLocalMessagesRemoveMessage(Account account, String folder, Message message) {
                MessageInfoHolder holder = getMessage(message);
                if (holder != null) {
                    removeMessages(Collections.singletonList(holder));
                }
            }

            @Override
            public void listLocalMessagesAddMessages(Account account, String folder, List<Message> messages) {
                addOrUpdateMessages(account, folder, messages, false);
            }

            @Override
            public void listLocalMessagesUpdateMessage(Account account, String folder, Message message) {
                addOrUpdateMessage(account, folder, message, false);
            }

            @Override
            public void searchStats(AccountStats stats) {
                mUnreadMessageCount = stats.unreadMessageCount;
                super.searchStats(stats);
            }

            @Override
            public void folderStatusChanged(Account account, String folder, int unreadMessageCount) {
                if (updateForMe(account, folder)) {
                    mUnreadMessageCount = unreadMessageCount;
                }
                super.folderStatusChanged(account, folder, unreadMessageCount);
            }

            @Override
            public void messageUidChanged(Account account, String folder, String oldUid, String newUid) {
                MessageReference ref = new MessageReference();
                ref.accountUuid = account.getUuid();
                ref.folderName = folder;
                ref.uid = oldUid;

                MessageInfoHolder holder = getMessage(ref);
                if (holder != null) {
                    holder.uid = newUid;
                    holder.message.setUid(newUid);
                }
            }
            @Override
            public void loadMessageForViewHeadersAvailable(final Account account, String folder, String uid,
            final Message message) {
                if (mCurrentMessageInfo == null || !mCurrentMessageInfo.uid.equals(uid) || !mCurrentMessageInfo.folder.name.equals(folder)
                || !mCurrentMessageInfo.accountUuid.equals(account.getUuid())) {
                    return;
                }
                MessageList.this.mMessage = message;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (!message.isSet(Flag.X_DOWNLOADED_FULL) && !message.isSet(Flag.X_DOWNLOADED_PARTIAL)) {
                            mMessageView.loadBodyFromUrl("file:///android_asset/downloading.html");
                        }
                        mMessageView.setHeaders(message, account);
                        mMessageView.setOnFlagListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                onToggleFlag(mCurrentMessageInfo);
                            }
                        });
                    }
                });
            }

            @Override
            public void loadMessageForViewBodyAvailable(Account account, String folder, String uid,
            Message message) {
                if (mCurrentMessageInfo == null || !mCurrentMessageInfo.uid.equals(uid) || !mCurrentMessageInfo.folder.name.equals(folder)
                || !mCurrentMessageInfo.accountUuid.equals(account.getUuid())) {
                    return;
                }


                displayMessageBody(account, folder, uid, message);
            }


            @Override
            public void loadMessageForViewFailed(Account account, String folder, String uid,
            final Throwable t) {
                if (mCurrentMessageInfo == null
                        || !mCurrentMessageInfo.uid.equals(uid) || !mCurrentMessageInfo.folder.name.equals(folder)
                || !mCurrentMessageInfo.accountUuid.equals(account.getUuid())) {
                    return;
                }
                mHandler.post(new Runnable() {
                    public void run() {
                        setProgressBarIndeterminateVisibility(false);
                        if (t instanceof IllegalArgumentException) {
                            mHandler.invalidIdError();
                        } else {
                            mHandler.networkError();
                        }
                        if ((MessageList.this.mMessage == null) ||
                        !MessageList.this.mMessage.isSet(Flag.X_DOWNLOADED_PARTIAL)) {
                            mMessageView.loadBodyFromUrl("file:///android_asset/empty.html");
                        }
                    }
                });
            }

            @Override
            public void loadMessageForViewFinished(Account account, String folder, String uid,
            final Message message) {
                if (mCurrentMessageInfo == null || !mCurrentMessageInfo.uid.equals(uid) || !mCurrentMessageInfo.folder.name.equals(folder)
                || !mCurrentMessageInfo.accountUuid.equals(account.getUuid())) {
                    return;
                }
                mHandler.post(new Runnable() {
                    public void run() {
                        setProgressBarIndeterminateVisibility(false);
                        mMessageView.setShowDownloadButton(message);
                    }
                });
            }

            @Override
            public void loadMessageForViewStarted(Account account, String folder, String uid) {
                if (mCurrentMessageInfo == null
                        || !mCurrentMessageInfo.uid.equals(uid)
                        || !mCurrentMessageInfo.folder.name.equals(folder)
                || !mCurrentMessageInfo.accountUuid.equals(account.getUuid())) {
                    return;
                }
                mHandler.post(new Runnable() {
                    public void run() {
                        setProgressBarIndeterminateVisibility(true);
                    }
                });
            }

            @Override
            public void loadAttachmentStarted(Account account, Message message,
            Part part, Object tag, boolean requiresDownload) {
                if (mMessage == null || mMessage != message) {
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMessageView.setAttachmentsEnabled(false);
                    }
                });
                mHandler.progress(true);
                if (requiresDownload) {
                    mHandler.fetchingAttachment();
                }
            }

            @Override
            public void loadAttachmentFinished(Account account, Message message, Part part, Object tag) {
                if (mMessage != message) {
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMessageView.setAttachmentsEnabled(true);
                        mHandler.progress(false);
                    }
                });
                Object[] params = (Object[]) tag;
                boolean download = (Boolean) params[0];
                AttachmentView attachment = (AttachmentView) params[1];
                if (download) {
                    attachment.writeFile();

                } else {
                    attachment.showFile();
                }
            }

            @Override
            public void loadAttachmentFailed(Account account, Message message, Part part,
            Object tag, String reason) {
                if (mMessage != message) {
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMessageView.setAttachmentsEnabled(true);
                        mHandler.progress(false);
                        mHandler.networkError();
                    }
                });
            }


        };

        private boolean updateForMe(Account account, String folder) {
            if (mAccount != null && mAccount.equals(account) && mFolderName != null && mFolderName.equals(folder)) {
                return true;
            } else {
                return false;
            }
        }

        private Drawable mAttachmentIcon;
        private Drawable mAnsweredIcon;
        private View footerView = null;

        MessageListAdapter() {
            mAttachmentIcon = getResources().getDrawable(R.drawable.ic_email_attachment_small);
            mAnsweredIcon = getResources().getDrawable(R.drawable.ic_email_answered_small);
        }

        public void markAllMessagesAsDirty() {
            for (MessageInfoHolder holder : mAdapter.messages) {
                holder.dirty = true;
            }
        }
        public void pruneDirtyMessages() {
            synchronized (mAdapter.messages) {
                Iterator<MessageInfoHolder> iter = mAdapter.messages.iterator();
                while (iter.hasNext()) {
                    MessageInfoHolder holder = iter.next();
                    if (holder.dirty) {
                        if (holder.selected) {
                            mSelectedCount--;
                            toggleBatchMode();
                        }
                        mAdapter.removeMessages(Collections.singletonList(holder));
                    }
                }
            }
        }

        /**
         * @param holders
         *            Never {@code null}.
         */
        public void removeMessages(List<MessageInfoHolder> holders) {
            mHandler.removeMessages(holders);
        }

        private void addOrUpdateMessage(Account account, String folderName, Message message, boolean verifyAgainstSearch) {
            List<Message> messages = new ArrayList<Message>();
            messages.add(message);
            addOrUpdateMessages(account, folderName, messages, verifyAgainstSearch);
        }

        private void addOrUpdateMessages(final Account account, final String folderName, final List<Message> providedMessages, final boolean verifyAgainstSearch) {
            // we copy the message list because the callback doesn't expect
            // the callbacks to mutate it.
            final List<Message> messages = new ArrayList<Message>(providedMessages);

            boolean needsSort = false;
            final List<MessageInfoHolder> messagesToAdd = new ArrayList<MessageInfoHolder>();
            List<MessageInfoHolder> messagesToRemove = new ArrayList<MessageInfoHolder>();
            List<Message> messagesToSearch = new ArrayList<Message>();

            // cache field into local variable for faster access for JVM without JIT
            final MessageHelper messageHelper = mMessageHelper;

            for (Message message : messages) {
                MessageInfoHolder m = getMessage(message);
                if (message.isSet(Flag.DELETED)) {
                    if (m != null) {
                        messagesToRemove.add(m);
                    }
                } else {
                    final Folder messageFolder = message.getFolder();
                    final Account messageAccount = messageFolder.getAccount();
                    if (m == null) {
                        if (updateForMe(account, folderName)) {
                            m = new MessageInfoHolder();
                            messageHelper.populate(m, message, new FolderInfoHolder(MessageList.this, messageFolder, messageAccount), messageAccount);
                            messagesToAdd.add(m);
                        } else {
                            if (mQueryString != null) {
                                if (verifyAgainstSearch) {
                                    messagesToSearch.add(message);
                                } else {
                                    m = new MessageInfoHolder();
                                    messageHelper.populate(m, message, new FolderInfoHolder(MessageList.this, messageFolder, messageAccount), messageAccount);
                                    messagesToAdd.add(m);
                                }
                            }
                        }
                    } else {
                        m.dirty = false; // as we reload the message, unset its dirty flag
                        messageHelper.populate(m, message, new FolderInfoHolder(MessageList.this, messageFolder, account), account);
                        needsSort = true;
                    }
                }
            }

            if (messagesToSearch.size() > 0) {
                mController.searchLocalMessages(mAccountUuids, mFolderNames, messagesToSearch.toArray(EMPTY_MESSAGE_ARRAY), mQueryString, mIntegrate, mQueryFlags, mForbiddenFlags,
                new MessagingListener() {
                    @Override
                    public void listLocalMessagesAddMessages(Account account, String folder, List<Message> messages) {
                        addOrUpdateMessages(account, folder, messages, false);
                    }
                });
            }

            if (messagesToRemove.size() > 0) {
                removeMessages(messagesToRemove);
            }

            if (messagesToAdd.size() > 0) {
                mHandler.addMessages(messagesToAdd);
            }

            if (needsSort) {
                mHandler.sortMessages();
                mHandler.resetUnreadCount();
            }
        }
        public MessageInfoHolder getMessage(Message message) {
            return getMessage(message.makeMessageReference());
        }

        // XXX TODO - make this not use a for loop
        public MessageInfoHolder getMessage(MessageReference messageReference) {
            synchronized (mAdapter.messages) {
                for (MessageInfoHolder holder : mAdapter.messages) {
                    /*
                     * 2010-06-21 - cketti
                     * Added null pointer check. Not sure what's causing 'holder'
                     * to be null. See log provided in issue 1749, comment #15.
                     *
                     * Please remove this comment once the cause was found and the
                     * bug(?) fixed.
                     */
                    if ((holder != null) && holder.message.equalsReference(messageReference)) {
                        return holder;
                    }
                }
            }
            return null;
        }

        public FolderInfoHolder getFolder(String folder, Account account) {
            LocalFolder local_folder = null;
            try {
                LocalStore localStore = account.getLocalStore();
                local_folder = localStore.getFolder(folder);
                return new FolderInfoHolder(context, local_folder, account);
            } catch (Exception e) {
                Log.e(K9.LOG_TAG, "getFolder(" + folder + ") goes boom: ", e);
                return null;
            } finally {
                if (local_folder != null) {
                    local_folder.close();
                }
            }
        }

        private final OnClickListener flagClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Perform action on clicks
                MessageInfoHolder message = (MessageInfoHolder) getItem((Integer)v.getTag());
                onToggleFlag(message);
            }
        };

        @Override
        public int getCount() {
            return messages.size();
        }

        @Override
        public long getItemId(int position) {
            try {
                MessageInfoHolder messageHolder = (MessageInfoHolder) getItem(position);
                if (messageHolder != null) {
                    return messageHolder.message.getId();
                }
            } catch (Exception e) {
                Log.i(K9.LOG_TAG, "getItemId(" + position + ") ", e);
            }
            return -1;
        }

        public Object getItem(long position) {
            return getItem((int)position);
        }

        @Override
        public Object getItem(int position) {
            try {
                synchronized (mAdapter.messages) {
                    if (position < mAdapter.messages.size()) {
                        return mAdapter.messages.get(position);
                    }
                }
            } catch (Exception e) {
                Log.e(K9.LOG_TAG, "getItem(" + position + "), but folder.messages.size() = " + mAdapter.messages.size(), e);
            }
            return null;
        }


        public View getView(int position, View convertView, ViewGroup parent) {
            MessageInfoHolder message = (MessageInfoHolder) getItem(position);
            View view;

            if ((convertView != null) && (convertView.getId() == R.layout.message_list_item)) {
                view = convertView;
            } else {
                if (mTouchView) {
                    view = mInflater.inflate(R.layout.message_list_item_touchable, parent, false);
                } else {
                    view = mInflater.inflate(R.layout.message_list_item, parent, false);
                }

                view.setId(R.layout.message_list_item);
            }

            MessageViewHolder holder = (MessageViewHolder) view.getTag();

            if (holder == null) {
                holder = new MessageViewHolder();
                holder.subject = (TextView) view.findViewById(R.id.subject);
                holder.from = (TextView) view.findViewById(R.id.from);
                holder.date = (TextView) view.findViewById(R.id.date);
                holder.chip = view.findViewById(R.id.chip);
                holder.preview = (TextView) view.findViewById(R.id.preview);
                holder.selected = (CheckBox) view.findViewById(R.id.selected_checkbox);
                holder.flagged = (CheckBox) view.findViewById(R.id.flagged);

                holder.flagged.setOnClickListener(flagClickListener);

                if (!mStars) {
                    holder.flagged.setVisibility(View.GONE);
                }

                if (mCheckboxes) {
                    holder.selected.setVisibility(View.VISIBLE);
                }

                if (holder.selected != null) {
                    holder.selected.setOnCheckedChangeListener(holder);
                }
                holder.subject.setTextSize(TypedValue.COMPLEX_UNIT_SP, mFontSizes.getMessageListSubject());
                holder.date.setTextSize(TypedValue.COMPLEX_UNIT_SP, mFontSizes.getMessageListDate());

                if (mTouchView) {
                    holder.preview.setLines(mPreviewLines);
                    holder.preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, mFontSizes.getMessageListPreview());

                } else {
                    holder.from.setTextSize(TypedValue.COMPLEX_UNIT_SP, mFontSizes.getMessageListSender());
                }

                view.setTag(holder);
            }

            if (message != null) {
                bindView(position, view, holder, message);
            } else {
                // This branch code is triggered when the local store
                // hands us an invalid message

                holder.chip.getBackground().setAlpha(0);
                holder.subject.setText(getString(R.string.general_no_subject));
                holder.subject.setTypeface(null, Typeface.NORMAL);
                String noSender = getString(R.string.general_no_sender);
                if (holder.preview != null) {
                    holder.preview.setText(noSender, TextView.BufferType.SPANNABLE);
                    Spannable str = (Spannable) holder.preview.getText();

                    str.setSpan(new StyleSpan(Typeface.NORMAL),
                                0,
                                noSender.length(),
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    str.setSpan(K9.createAbsoluteSizeSpan(mFontSizes.getMessageListSender()),
                                0,
                                noSender.length(),
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    holder.from.setText(noSender);
                    holder.from.setTypeface(null, Typeface.NORMAL);
                    holder.from.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                }

                holder.date.setText(getString(R.string.general_no_date));

                //WARNING: Order of the next 2 lines matter
                holder.position = -1;
                holder.selected.setChecked(false);

                if (!mCheckboxes) {
                    holder.selected.setVisibility(View.GONE);
                }
                holder.flagged.setChecked(false);
            }


            return view;
        }

        /**
         * Associate model data to view object.
         *
         * @param position
         *            The position of the item within the adapter's data set of
         *            the item whose view we want.
         * @param view
         *            Main view component to alter. Never <code>null</code>.
         * @param holder
         *            Convenience view holder - eases access to <tt>view</tt>
         *            child views. Never <code>null</code>.
         * @param message
         *            Never <code>null</code>.
         */
        private void bindView(final int position, final View view, final MessageViewHolder holder,
                              final MessageInfoHolder message) {
            holder.subject.setTypeface(null, message.read ? Typeface.NORMAL : Typeface.BOLD);

            // XXX TODO there has to be some way to walk our view hierarchy and get this
            holder.flagged.setTag(position);
            holder.flagged.setChecked(message.flagged);

            // So that the mSelectedCount is only incremented/decremented
            // when a user checks the checkbox (vs code)
            holder.position = -1;
            holder.selected.setChecked(message.selected);

            if (!mCheckboxes) {
                holder.selected.setVisibility(message.selected ? View.VISIBLE : View.GONE);
            }



            holder.chip.setBackgroundDrawable(message.message.getFolder().getAccount().generateColorChip().drawable());
            holder.chip.getBackground().setAlpha(message.read ? 127 : 255);
            view.getBackground().setAlpha(message.downloaded ? 0 : 127);

            if ((message.message.getSubject() == null) || message.message.getSubject().equals("")) {
                holder.subject.setText(getText(R.string.general_no_subject));
            } else {
                holder.subject.setText(message.message.getSubject());
            }

            int senderTypeface = message.read ? Typeface.NORMAL : Typeface.BOLD;
            if (holder.preview != null) {
                /*
                 * In the touchable UI, we have previews. Otherwise, we
                 * have just a "from" line.
                 * Because text views can't wrap around each other(?) we
                 * compose a custom view containing the preview and the
                 * from.
                 */

                holder.preview.setText(new SpannableStringBuilder(recipientSigil(message))
                                       .append(message.sender).append(" ").append(message.message.getPreview()),
                                       TextView.BufferType.SPANNABLE);
                Spannable str = (Spannable)holder.preview.getText();

                // Create a span section for the sender, and assign the correct font size and weight.
                str.setSpan(new StyleSpan(senderTypeface),
                            0,
                            message.sender.length() + 1,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                str.setSpan(K9.createAbsoluteSizeSpan(mFontSizes.getMessageListSender()),
                            0,
                            message.sender.length() + 1,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                // set span for preview message.
                str.setSpan(new ForegroundColorSpan(Color.rgb(128, 128, 128)), // How do I can specify the android.R.attr.textColorTertiary
                            message.sender.length() + 1,
                            str.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                holder.from.setText(new SpannableStringBuilder(recipientSigil(message)).append(message.sender));

                holder.from.setTypeface(null, senderTypeface);
            }

            holder.date.setText(message.getDate(mMessageHelper));
            holder.subject.setCompoundDrawablesWithIntrinsicBounds(
                message.answered ? mAnsweredIcon : null, // left
                null, // top
                message.message.hasAttachments() ? mAttachmentIcon : null, // right
                null); // bottom
            holder.position = position;
        }

        private String recipientSigil(MessageInfoHolder message) {
            if (message.message.toMe()) {
                return getString(R.string.messagelist_sent_to_me_sigil);
            } else if (message.message.ccMe()) {
                return getString(R.string.messagelist_sent_cc_me_sigil);
            } else {
                return "";
            }
        }


        @Override
        public boolean hasStableIds() {
            return true;
        }

        public boolean isItemSelectable(int position) {
            if (position < mAdapter.messages.size()) {
                return true;
            } else {
                return false;
            }
        }
    }

    class MessageViewHolder
        implements OnCheckedChangeListener {
        public TextView subject;
        public TextView preview;
        public TextView from;
        public TextView time;
        public TextView date;
        public CheckBox flagged;
        public View chip;
        public CheckBox selected;
        public int position = -1;

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (position != -1) {
                MessageInfoHolder message = (MessageInfoHolder) mAdapter.getItem(position);
                if (message.selected != isChecked) {
                    if (isChecked) {
                        mSelectedCount++;
                    } else if (mSelectedCount > 0) {
                        mSelectedCount--;
                    }

                    // We must set the flag before showing the buttons as the
                    // buttons text depends on what is selected.
                    message.selected = isChecked;
                    if (!mCheckboxes) {
                        if (isChecked) {
                            selected.setVisibility(View.VISIBLE);
                        } else {
                            selected.setVisibility(View.GONE);
                        }
                    }
                    toggleBatchMode();
                }
            }
        }
    }


    private View getFooterView(ViewGroup parent) {
        if (mFooterView == null) {
            mFooterView = mInflater.inflate(R.layout.message_list_item_footer, parent, false);
            if (mQueryString != null) {
                mFooterView.setVisibility(View.GONE);
            }
            mFooterView.setId(R.layout.message_list_item_footer);
            FooterViewHolder holder = new FooterViewHolder();
            holder.progress = (ProgressBar) mFooterView.findViewById(R.id.message_list_progress);
            holder.progress.setIndeterminate(true);
            holder.main = (TextView) mFooterView.findViewById(R.id.main_text);
            mFooterView.setTag(holder);
        }

        return mFooterView;
    }

    private void updateFooterView() {
        FooterViewHolder holder = (FooterViewHolder) mFooterView.getTag();

        if (mCurrentFolder != null && mAccount != null) {
            if (mCurrentFolder.loading) {
                holder.main.setText(getString(R.string.status_loading_more));
                holder.progress.setVisibility(ProgressBar.VISIBLE);
            } else {
                if (!mCurrentFolder.lastCheckFailed) {
                    if (mAccount.getDisplayCount() == 0) {
                        holder.main.setText(getString(R.string.message_list_load_more_messages_action));
                    } else {
                        holder.main.setText(String.format(getString(R.string.load_more_messages_fmt), mAccount.getDisplayCount()));
                    }
                } else {
                    holder.main.setText(getString(R.string.status_loading_more_failed));
                }
                holder.progress.setVisibility(ProgressBar.INVISIBLE);
            }
        } else {
            holder.progress.setVisibility(ProgressBar.INVISIBLE);
        }
    }



    private void toggleBatchMode() {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (mSelectedCount < 0) {
                    mSelectedCount = 0;
                }

                if (mSelectedCount == 0) {
                    initializeActionBar();
                } else {
                    initializeBatchActionBar();
                }

                setActionBarTitle();

            }
        });


    }

    static class FooterViewHolder {
        public ProgressBar progress;
        public TextView main;
    }


    private boolean computeBatchDirection(boolean flagged) {
        boolean newState = false;

        synchronized (mAdapter.messages) {
            for (MessageInfoHolder holder : mAdapter.messages) {
                if (holder.selected) {
                    if (flagged) {
                        if (!holder.flagged) {
                            newState = true;
                            break;
                        }
                    } else {
                        if (!holder.read) {
                            newState = true;
                            break;
                        }
                    }
                }
            }
        }
        return newState;
    }

    private boolean anySelected() {
        synchronized (mAdapter.messages) {
            for (MessageInfoHolder holder : mAdapter.messages) {
                if (holder.selected) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onClick(View v) {


        switch (v.getId()) {
        case R.id.delete:
            if (mCurrentMessageInfo != null) {
                onDelete(getSelectionFromMessage(mCurrentMessageInfo));
            }
            return;
        case R.id.move:
            if (mCurrentMessageInfo != null) {
                onMove(getSelectionFromMessage(mCurrentMessageInfo));
            }
            return;
        case R.id.marknew:
            if (mCurrentMessageInfo != null) {
                onToggleRead(mCurrentMessageInfo);
            }
            return;
        case R.id.next:
            gotoNextItem();
            return;
        case R.id.previous:
            gotoPreviousItem();
            return;
        case R.id.download:
            ((AttachmentView)v).saveFile();
            return;
        case R.id.show_pictures:
            mMessageView.setLoadPictures(true);
            return;
        case R.id.download_remainder:
            onDownloadRemainder();
            return;
        }

    }

    @Override
    public void onAnimationEnd(Animation animation) {
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
    }

    @Override
    public void onAnimationStart(Animation animation) {
    }



    private void setAllSelected(boolean isSelected) {
        mSelectedCount = 0;
        synchronized (mAdapter.messages) {
            for (MessageInfoHolder holder : mAdapter.messages) {
                holder.selected = isSelected;
                mSelectedCount += (isSelected ? 1 : 0);
            }
        }
        mAdapter.notifyDataSetChanged();
        toggleBatchMode();
    }

    private void setSelected(final List<MessageInfoHolder> holders, final boolean newState) {
        for (final MessageInfoHolder holder : holders) {
            if (holder.selected != newState) {
                holder.selected = newState;
                mSelectedCount += (newState ? 1 : -1);
            }
        }
        mAdapter.notifyDataSetChanged();
        toggleBatchMode();
    }

    /**
     * @param holders
     *            Messages to update. Never {@code null}.
     * @param flag
     *            Flag to be updated on the specified messages. Never
     *            {@code null}.
     * @param newState
     *            State to set for the given flag.
     */
    private void setFlag(final List<MessageInfoHolder> holders, final Flag flag, final boolean newState) {
        if (holders.isEmpty()) {
            return;
        }
        final Message[] messageList = new Message[holders.size()];
        int i = 0;
        for (final Iterator<MessageInfoHolder> iterator = holders.iterator(); iterator.hasNext(); i++) {
            final MessageInfoHolder holder = iterator.next();
            messageList[i] = holder.message;
            if (flag == Flag.SEEN) {
                holder.read = newState;
            } else if (flag == Flag.FLAGGED) {
                holder.flagged = newState;
            }
        }
        mController.setFlag(messageList, flag, newState);
        mHandler.sortMessages();
        toggleBatchMode();
    }

    /**
     * Display the message move activity.
     *
     * @param holders
     *            Never {@code null}.
     */
    private void onMove(final List<MessageInfoHolder> holders) {
        if (!checkCopyOrMovePossible(holders, FolderOperation.MOVE)) {
            return;
        }

        final Folder folder = holders.size() == 1 ? holders.get(0).message.getFolder() : mCurrentFolder.folder;
        displayFolderChoice(ACTIVITY_CHOOSE_FOLDER_MOVE, folder, holders);
    }

    /**
     * Display the message copy activity.
     *
     * @param holders
     *            Never {@code null}.
     */
    private void onCopy(final List<MessageInfoHolder> holders) {
        if (!checkCopyOrMovePossible(holders, FolderOperation.COPY)) {
            return;
        }

        final Folder folder = holders.size() == 1 ? holders.get(0).message.getFolder() : mCurrentFolder.folder;
        displayFolderChoice(ACTIVITY_CHOOSE_FOLDER_COPY, folder, holders);
    }

    /**
     * Helper method to manage the invocation of
     * {@link #startActivityForResult(Intent, int)} for a folder operation
     * ({@link ChooseFolder} activity), while saving a list of associated
     * messages.
     *
     * @param requestCode
     *            If >= 0, this code will be returned in onActivityResult() when
     *            the activity exits.
     * @param folder
     *            Never {@code null}.
     * @param holders
     *            Messages to be affected by the folder operation. Never
     *            {@code null}.
     * @see #startActivityForResult(Intent, int)
     */
    private void displayFolderChoice(final int requestCode, final Folder folder, final List<MessageInfoHolder> holders) {
        final Intent intent = new Intent(this, ChooseFolder.class);
        intent.putExtra(ChooseFolder.EXTRA_ACCOUNT, folder.getAccount().getUuid());
        intent.putExtra(ChooseFolder.EXTRA_CUR_FOLDER, folder.getName());
        intent.putExtra(ChooseFolder.EXTRA_SEL_FOLDER, folder.getAccount().getLastSelectedFolderName());
        // remember the selected messages for #onActivityResult
        mActiveMessages = holders;
        startActivityForResult(intent, requestCode);
    }

    /**
     * @param holders
     *            Never {@code null}.
     */
    private void onArchive(final List<MessageInfoHolder> holders) {
        final String folderName = holders.get(0).message.getFolder().getAccount().getArchiveFolderName();
        move(holders, folderName);
    }

    /**
     * @param holders
     *            Never {@code null}.
     */
    private void onSpam(final List<MessageInfoHolder> holders) {
        if (K9.confirmSpam()) {
            // remember the message selection for #onCreateDialog(int)
            mActiveMessages = holders;
            showDialog(R.id.dialog_confirm_spam);
        } else {
            onSpamConfirmed(holders);
        }
    }

    /**
     * @param holders
     *            Never {@code null}.
     */
    private void onSpamConfirmed(final List<MessageInfoHolder> holders) {
        final String folderName = holders.get(0).message.getFolder().getAccount().getSpamFolderName();
        // TODO one should separate messages by account and call move afterwards
        // (because each account might have a specific Spam folder name)
        move(holders, folderName);
    }

    private static enum FolderOperation {
        COPY, MOVE
    }

    /**
     * Display an Toast message if any message isn't synchronized
     *
     * @param holders
     *            Never <code>null</code>.
     * @param operation
     *            Never {@code null}.
     *
     * @return <code>true</code> if operation is possible
     */
    private boolean checkCopyOrMovePossible(final List<MessageInfoHolder> holders, final FolderOperation operation) {
        if (holders.isEmpty()) {
            return false;
        }
        boolean first = true;
        for (final MessageInfoHolder holder : holders) {
            final Message message = holder.message;
            if (first) {
                first = false;
                // account check
                final Account account = message.getFolder().getAccount();
                if ((operation == FolderOperation.MOVE && !mController.isMoveCapable(account)) || (operation == FolderOperation.COPY && !mController.isCopyCapable(account))) {
                    return false;
                }
            }
            // message check
            if ((operation == FolderOperation.MOVE && !mController.isMoveCapable(message)) || (operation == FolderOperation.COPY && !mController.isCopyCapable(message))) {
                final Toast toast = Toast.makeText(this, R.string.move_copy_cannot_copy_unsynced_message,
                                                   Toast.LENGTH_LONG);
                toast.show();
                return false;
            }
        }
        return true;
    }

    /**
     * Helper method to get a List of message ready to be processed. This implementation will return a list containing the sole argument.
     *
     * @param holder Never {@code null}.
     * @return Never {@code null}.
     */
    private List<MessageInfoHolder> getSelectionFromMessage(final MessageInfoHolder holder) {
        final List<MessageInfoHolder> selection = Collections.singletonList(holder);
        return selection;
    }

    /**
     * Helper method to get a List of message ready to be processed. This implementation will iterate over messages and choose the checked ones.
     *
     * @return Never {@code null}.
     */
    private List<MessageInfoHolder> getSelectionFromCheckboxes() {
        final List<MessageInfoHolder> selection = new ArrayList<MessageInfoHolder>();
        synchronized (mAdapter.messages) {
            for (final MessageInfoHolder holder : mAdapter.messages) {
                if (holder.selected) {
                    selection.add(holder);
                }
            }
        }
        return selection;
    }

    /**
     * Copy the specified messages to the specified folder.
     *
     * @param holders Never {@code null}.
     * @param destination Never {@code null}.
     */
    private void copy(final List<MessageInfoHolder> holders, final String destination) {
        copyOrMove(holders, destination, FolderOperation.COPY);
    }

    /**
     * Move the specified messages to the specified folder.
     *
     * @param holders Never {@code null}.
     * @param destination Never {@code null}.
     */
    private void move(final List<MessageInfoHolder> holders, final String destination) {
        copyOrMove(holders, destination, FolderOperation.MOVE);
    }

    /**
     * The underlying implementation for {@link #copy(List, String)} and
     * {@link #move(List, String)}. This method was added mainly because those 2
     * methods share common behavior.
     *
     * @param holders
     *            Never {@code null}.
     * @param destination
     *            Never {@code null}.
     * @param operation
     *            Never {@code null}.
     */
    private void copyOrMove(final List<MessageInfoHolder> holders, final String destination, final FolderOperation operation) {
        if (K9.FOLDER_NONE.equalsIgnoreCase(destination)) {
            return;
        }
        boolean first = true;
        boolean isCurrentMessageInSet = false;
        Account account = null;
        String folderName = null;

        final List<Message> messages = new ArrayList<Message>(holders.size());

        for (final MessageInfoHolder holder : holders) {
            final Message message = holder.message;
            if (first) {
                first = false;
                folderName = message.getFolder().getName();
                account = message.getFolder().getAccount();
                if ((operation == FolderOperation.MOVE && !mController.isMoveCapable(account)) || (operation == FolderOperation.COPY && !mController.isCopyCapable(account))) {
                    // account is not copy/move capable
                    return;
                }
            } else if (!account.equals(message.getFolder().getAccount())
                       || !folderName.equals(message.getFolder().getName())) {
                // make sure all messages come from the same account/folder?
                return;
            }
            if ((operation == FolderOperation.MOVE && !mController.isMoveCapable(message)) || (operation == FolderOperation.COPY && !mController.isCopyCapable(message))) {
                final Toast toast = Toast.makeText(this, R.string.move_copy_cannot_copy_unsynced_message,
                                                   Toast.LENGTH_LONG);
                toast.show();

                // XXX return meaningful error value?

                // message isn't synchronized
                return;
            }

            messages.add(message);

            // only change the current message being viewed if the one deleted was the current one
            if (mCurrentMessageInfo != null && holder.uid == mCurrentMessageInfo.uid) {
                isCurrentMessageInSet = true;
            }


        }

        if (operation == FolderOperation.MOVE) {
            mController.moveMessages(account, folderName, messages.toArray(new Message[messages.size()]), destination,
                                     null);
            mHandler.removeMessages(holders);
            if (isCurrentMessageInSet) {
                showNextMessageOrReturn();
            }
        } else {
            mController.copyMessages(account, folderName, messages.toArray(new Message[messages.size()]), destination,
                                     null);
        }
        toggleBatchMode();
    }


    protected void onAccountUnavailable() {
        finish();
        // TODO inform user about account unavailability using Toast
        Accounts.listAccounts(this);
    }

// utilities


// XXX do we actually need this? why? - presumably for API v ??? compatibility
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean ret = false;
        if (KeyEvent.ACTION_DOWN == event.getAction()) {
            ret = onKeyDown(event.getKeyCode(), event);
        }
        if (!ret) {
            ret = super.dispatchKeyEvent(event);
        }
        return ret;
    }

    private void setupButtonViews() {
        setOnClickListener(R.id.from);
        setOnClickListener(R.id.next);
        setOnClickListener(R.id.previous);
        setOnClickListener(R.id.move);
        setOnClickListener(R.id.delete);
        setOnClickListener(R.id.marknew);
        setOnClickListener(R.id.spam);
        // To show full header
        setOnClickListener(R.id.header_container);
        setOnClickListener(R.id.show_pictures);
        setOnClickListener(R.id.download_remainder);


        mNext = findViewById(R.id.next);
        mPrevious = findViewById(R.id.previous);
        mDelete = findViewById(R.id.delete);
        mMove = findViewById(R.id.move);
    }

    private void setupDisplayMessageButtons() {
        mDelete.setEnabled(true);
        mNext.setEnabled(mNextMessage != null);
        mPrevious.setEnabled(mPreviousMessage != null);
        mMove.setEnabled(mController.isMoveCapable(mAccount));
    }



    private void disableButtons() {
        mMessageView.setLoadPictures(false);
        mMove.setEnabled(false);
        mNext.setEnabled(false);
        mPrevious.setEnabled(false);
        mDelete.setEnabled(false);
    }


    private void setOnClickListener(int viewCode) {
        View thisView = findViewById(viewCode);
        if (thisView != null) {
            thisView.setOnClickListener(this);
        }
    }
// This REALLY should be in MessageCryptoView
    public void onDecryptDone(PgpData pgpData) {
        // TODO: this might not be enough if the orientation was changed while in APG,
        // sometimes shows the original encrypted content
        mMessageView.loadBodyFromText(mAccount.getCryptoProvider(), mPgpData, mMessage, mPgpData.getDecryptedData(), "text/plain");
    }

    private void saveListViewSize() {
        SharedPreferences preferences = Preferences.getPreferences(this).getPreferences();
        K9.setPrimaryMessageListContentSize(mSplitView.getPrimaryContentSize());
        Editor editor = preferences.edit();
        K9.save(editor);
        editor.commit();
    }
}
