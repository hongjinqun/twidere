/*
 *				Twidere - Twitter client for Android
 * 
 * Copyright (C) 2012 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.fragment;

import static org.mariotaku.twidere.util.Utils.addIntentToSubMenu;
import static org.mariotaku.twidere.util.Utils.getAccountScreenName;
import static org.mariotaku.twidere.util.Utils.openUserListDetails;

import org.mariotaku.popupmenu.PopupMenu;
import org.mariotaku.popupmenu.PopupMenu.OnMenuItemClickListener;
import org.mariotaku.twidere.R;
import org.mariotaku.twidere.adapter.SeparatedListAdapter;
import org.mariotaku.twidere.adapter.UserListsAdapter;
import org.mariotaku.twidere.app.TwidereApplication;
import org.mariotaku.twidere.loader.UserListsLoader;
import org.mariotaku.twidere.loader.UserListsLoader.UserListsData;
import org.mariotaku.twidere.model.Panes;
import org.mariotaku.twidere.model.ParcelableUserList;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;

public class UserListsListFragment extends PullToRefreshListFragment implements
		LoaderCallbacks<UserListsLoader.UserListsData>, OnItemClickListener, OnItemLongClickListener, Panes.Left,
		OnMenuItemClickListener {

	private SeparatedListAdapter<UserListsAdapter> mAdapter;
	private UserListsAdapter mUserListsAdapter, mUserListMembershipsAdapter;

	private SharedPreferences mPreferences;
	private ListView mListView;
	private long mAccountId, mUserId;
	private String mScreenName;

	private PopupMenu mPopupMenu;
	private ParcelableUserList mSelectedUserList;

	private TwidereApplication mApplication;

	private UserListsData mUserListsData;

	private long mCursor = -1;
	private int mPage = 0;

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setHasOptionsMenu(true);
		mApplication = getApplication();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		final Bundle args = getArguments() != null ? getArguments() : new Bundle();
		if (args != null) {
			mAccountId = args.getLong(INTENT_KEY_ACCOUNT_ID, -1);
			mUserId = args.getLong(INTENT_KEY_USER_ID, -1);
			mScreenName = args.getString(INTENT_KEY_SCREEN_NAME);
		}
		mAdapter = new SeparatedListAdapter<UserListsAdapter>(getActivity());
		mUserListsAdapter = new UserListsAdapter(getActivity());
		mUserListMembershipsAdapter = new UserListsAdapter(getActivity());
		mAdapter.addSection(getString(R.string.users_lists), mUserListsAdapter);
		mAdapter.addSection(getString(R.string.lists_following_user), mUserListMembershipsAdapter);
		mListView = getListView();
		mAccountId = args.getLong(INTENT_KEY_ACCOUNT_ID, -1);
		setListAdapter(mAdapter);
		mListView.setOnItemClickListener(this);
		mListView.setOnItemLongClickListener(this);
		getLoaderManager().initLoader(0, getArguments(), this);
		setMode(Mode.PULL_UP_TO_REFRESH);
		setListShown(false);
	}

	@Override
	public Loader<UserListsLoader.UserListsData> onCreateLoader(final int id, final Bundle args) {
		setProgressBarIndeterminateVisibility(true);
		return new UserListsLoader(getActivity(), mAccountId, mUserId, mScreenName, mUserListsData, mCursor, mPage);
	}

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
		inflater.inflate(R.menu.menu_user_list_created, menu);
	}

	@Override
	public final void onItemClick(final AdapterView<?> adapter, final View view, final int position, final long id) {
		if (mApplication.isMultiSelectActive()) return;
		final Object selected = mAdapter.getItem(position - mListView.getHeaderViewsCount());
		final ParcelableUserList user_list = selected instanceof ParcelableUserList ? (ParcelableUserList) selected
				: null;
		if (user_list == null) return;
		openUserListDetails(getActivity(), mAccountId, user_list.list_id, user_list.user_id,
				user_list.user_screen_name, user_list.name);
	}

	@Override
	public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, final long id) {
		if (mApplication.isMultiSelectActive()) return true;
		mSelectedUserList = null;
		final ListAdapter adapter = getListAdapter();
		final Object selected = adapter.getItem(position - mListView.getHeaderViewsCount());
		mSelectedUserList = selected instanceof ParcelableUserList ? (ParcelableUserList) selected : null;
		if (mSelectedUserList == null) return false;
		mPopupMenu = PopupMenu.getInstance(getActivity(), view);
		mPopupMenu.inflate(R.menu.action_user_list);
		final Menu menu = mPopupMenu.getMenu();
		final MenuItem extensions = menu.findItem(MENU_EXTENSIONS_SUBMENU);
		if (extensions != null) {
			final Intent intent = new Intent(INTENT_ACTION_EXTENSION_OPEN_USER_LIST);
			final Bundle extras = new Bundle();
			extras.putParcelable(INTENT_KEY_USER_LIST, mSelectedUserList);
			intent.putExtras(extras);
			addIntentToSubMenu(getActivity(), extensions.getSubMenu(), intent);
		}
		mPopupMenu.setOnMenuItemClickListener(this);
		mPopupMenu.show();
		return true;
	}

	@Override
	public void onLoaderReset(final Loader<UserListsLoader.UserListsData> loader) {
		setProgressBarIndeterminateVisibility(false);
	}

	@Override
	public void onLoadFinished(final Loader<UserListsLoader.UserListsData> loader,
			final UserListsLoader.UserListsData data) {
		setProgressBarIndeterminateVisibility(false);
		mUserListsData = data;
		if (data != null) {
			mCursor = data.getNextCursor();
			mPage++;
			mUserListsAdapter.setData(data.getLists(), true);
			mUserListMembershipsAdapter.setData(data.getMemberships(), true);
			mAdapter.notifyDataSetChanged();
			invalidateOptionsMenu();
		}
		onRefreshComplete();
		setListShown(true);
	}

	@Override
	public boolean onMenuItemClick(final MenuItem item) {
		if (mSelectedUserList == null) return false;
		switch (item.getItemId()) {
			case MENU_VIEW_USER_LIST: {
				openUserListDetails(getActivity(), mAccountId, mSelectedUserList.list_id, mSelectedUserList.user_id,
						mSelectedUserList.user_screen_name, mSelectedUserList.name);
				break;
			}
			default: {
				if (item.getIntent() != null) {
					try {
						startActivity(item.getIntent());
					} catch (final ActivityNotFoundException e) {
						Log.w(LOGTAG, e);
						return false;
					}
				}
				break;
			}
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// TODO Auto-generated method stub
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onPrepareOptionsMenu(final Menu menu) {
		final MenuItem item = menu.findItem(R.id.new_user_list);
		if (item == null) return;
		final String screen_name = getAccountScreenName(getActivity(), mAccountId);
		item.setVisible(mUserId == mAccountId || screen_name != null && screen_name.equalsIgnoreCase(mScreenName));
	}

	@Override
	public void onPullDownToRefresh() {

	}

	@Override
	public void onPullUpToRefresh() {
		getLoaderManager().restartLoader(0, null, this);
	}

	@Override
	public void onStart() {
		super.onStart();
		final float text_size = mPreferences.getInt(PREFERENCE_KEY_TEXT_SIZE, PREFERENCE_DEFAULT_TEXT_SIZE);
		final boolean display_profile_image = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_PROFILE_IMAGE, true);
		for (final UserListsAdapter item : mAdapter.getAdapters()) {
			item.setDisplayProfileImage(display_profile_image);
			item.setTextSize(text_size);
		}
	}

	@Override
	public void onStop() {
		if (mPopupMenu != null) {
			mPopupMenu.dismiss();
		}
		super.onStop();
	}
}
