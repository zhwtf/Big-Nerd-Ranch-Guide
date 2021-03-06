package com.bignerdranch.android.criminalintent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/**
 * @author lidajun
 * @email solidajun@gmail.com
 * @date 16/6/5 13:49.
 * @desc: Crime列表页
 */
public class CrimeListFragment extends Fragment {
    private static final String TAG = "CrimeListFragment";
    private static final String SAVED_SUBTITLE_VISIBLE = "subtitle";
    private static final int REQUEST_CRIME = 1;
    private static final String ARG_SUBTITLE_VISIBLE = "arg_subtitle_visible";

    private RecyclerView mCrimeRecyclerView;
    private LinearLayout mEmptyLayout;
    private CrimeAdapter mAdapter;
    // 记录子标题状态
    private boolean mSubtitleVisible;
    private Callbacks mCallbacks;

    /**
     * Required interface for hosting activities
     */
    public interface Callbacks {
        void onCrimeSelected(Crime crime);
    }

    /**
     * 第10章 挑战练习：实现高效的RecyclerView刷新
     *
     * 该实现方式参考作者cstewart论坛的回复：Challenge Solution - having trouble
     * https://forums.bignerdranch.com/t/challenge-solution-having-trouble/7666/4
     *
     * 在后台被回收（设备旋转模拟这种情况）变为初始值-1，
     * 不需要使用onSaveInstanceState()保存起来的原因：
     * Now that I think about it, I guess you don't have to save the current index to the activity record.
     * If the activity is destroyed and has to be recreated,
     * then it has to reload/redraw the whole list anyway,
     * so there's no real need to save the index of the item that was edited.
     */
    private int mLastAdapterClickPosition = -1;

    private boolean mIsDeleteCrime;

    /**
     * 托管activity需要fragment实例时，转而调用newsInstance()方法，而非直接调用其构造方法。
     * 而且，为满足fragment创建argument的要求，activity可传入任何需要的参数给newInstance()方法。
     * @param subtitleVisible
     * @return
     */
    public static Fragment newIntent(boolean subtitleVisible) {
        Bundle args = new Bundle();
        args.putBoolean(ARG_SUBTITLE_VISIBLE, subtitleVisible);

        CrimeListFragment fragment = new CrimeListFragment();
        fragment.setArguments(args);// 附加argument给fragment
        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);// 通知FragmentManager，CrimeListFragment需接收选项菜单方法回调
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_crime_list, container, false);
        mCrimeRecyclerView = (RecyclerView) view.findViewById(R.id.crime_recycler_view);
        /*
         * 注意：没有LayoutManager的支持，不仅RecyclerView无法工作，还会导致崩溃
         * 原因：RecyclerView类的任务就是回收再利用以及定位屏幕上的TextView视图
         * 。实际上，定位的任务被委托给了LayoutManager。 没有LayoutManager，RecyclerView也就没法正常工作了。
         * -- 验证在recyclerview-v7:21.0.2版本中确实会崩溃。
         * 当前使用的recyclerview-v7:23.2.1最新版本并不会导致崩溃，但是界面无法显示内容，并打印出相应错误日志：
         * E/RecyclerView: No layout manager attached; skipping layout
         */
         mCrimeRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        mEmptyLayout = (LinearLayout) view.findViewById(R.id.crime_empty_view);
        Button btnAdd = (Button) view.findViewById(R.id.crime_add_button);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addCrime();
            }
        });

        /*
         * 从argument中获取subtitle Visible
         */
        mSubtitleVisible = getArguments().getBoolean(ARG_SUBTITLE_VISIBLE);

        if (savedInstanceState != null) {
            mSubtitleVisible = savedInstanceState.getBoolean(SAVED_SUBTITLE_VISIBLE);
        }

        updateUI();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SAVED_SUBTITLE_VISIBLE, mSubtitleVisible);// 保存子标题状态值（设备旋转问题）
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCallbacks = null;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_crime_list, menu);

        // 更新菜单项
        MenuItem subtitleItem = menu.findItem(R.id.menu_item_show_subtitle);
        if (mSubtitleVisible) {
            subtitleItem.setTitle(R.string.hide_subtitle);
        } else {
            subtitleItem.setTitle(R.string.show_subtitle);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_new_crime:
                addCrime();
                return true;
            case R.id.menu_item_show_subtitle:
                mSubtitleVisible = !mSubtitleVisible;
                getActivity().invalidateOptionsMenu();// 更新子标题，同时创建菜单项
                updateSubtitle();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void addCrime() {
        Crime crime = new Crime();
        CrimeLab.get(getActivity()).addCrime(crime);
        updateUI();
        mCallbacks.onCrimeSelected(crime);
    }

    /**
     * 设置工具栏子标题
     */
    private void updateSubtitle() {
        CrimeLab crimeLab = CrimeLab.get(getActivity());
        int crimeCount = crimeLab.getCrimes().size();
        // 使用getQuantityString方法正确处理单复数问题
        String subtitle = getResources().getQuantityString(R.plurals.subtitle_plural, crimeCount, crimeCount);

        // 根据mSubtitleVisible变量值，联动菜单项标题与子标题
        if (!mSubtitleVisible) {
            subtitle = null;
        }

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        activity.getSupportActionBar().setSubtitle(subtitle);
    }

    public void setDeleteCrime(boolean deleteCrime) {
        mIsDeleteCrime = deleteCrime;
    }

    public void updateUI() {
        CrimeLab crimeLab = CrimeLab.get(getActivity());
        List<Crime> crimes = crimeLab.getCrimes();

        if (crimes.isEmpty()) {
            mEmptyLayout.setVisibility(View.VISIBLE);
            mCrimeRecyclerView.setVisibility(View.GONE);
        } else {
            mEmptyLayout.setVisibility(View.GONE);
            mCrimeRecyclerView.setVisibility(View.VISIBLE);

            if (mAdapter == null) {
                mAdapter = new CrimeAdapter(crimes);
                mCrimeRecyclerView.setAdapter(mAdapter);
            } else {
                mAdapter.setCrimes(crimes);
                if (mLastAdapterClickPosition < 0) {
                    mAdapter.notifyDataSetChanged();
                } else if (mIsDeleteCrime) {
                    mAdapter.notifyItemRemoved(mLastAdapterClickPosition);
                    mLastAdapterClickPosition = -1;
                } else {
                    mAdapter.notifyItemChanged(mLastAdapterClickPosition);
                    mLastAdapterClickPosition = -1;
                }
            }
        }

        /*
         * 显示最新状态（onResume）
         * 解决问题一：新建crime记录后，使用回退按钮回到CrimeListActivity界面，子标题显示的总记录数不会更新
         */
        updateSubtitle();
    }

    private class CrimeHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private Crime mCrime;

        private TextView mTitleTextView;
        private TextView mDateTextView;
        private CheckBox mSolvedCheckBox;

        public CrimeHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);

            mTitleTextView = (TextView) itemView.findViewById(R.id.list_item_crime_title_text_view);
            mDateTextView = (TextView) itemView.findViewById(R.id.list_item_crime_date_text_view);
            mSolvedCheckBox = (CheckBox) itemView.findViewById(R.id.list_item_crime_solved_check_box);
        }

        public void bindCrime(Crime crime) {
            mCrime = crime;
            mTitleTextView.setText(mCrime.getTitle());
            mDateTextView.setText(mCrime.getDate().toString());
            mSolvedCheckBox.setChecked(mCrime.isSolved());
        }

        @Override
        public void onClick(View v) {
            mLastAdapterClickPosition = getAdapterPosition();
            mCallbacks.onCrimeSelected(mCrime);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CRIME) {
            if (resultCode == Activity.RESULT_OK) {
                mIsDeleteCrime  = data.getBooleanExtra(CrimeFragment.EXTRA_DELETE_CRIME, false);
            }
        }
    }

    private class CrimeAdapter extends RecyclerView.Adapter<CrimeHolder> {
        private List<Crime> mCrimes;

        public CrimeAdapter(List<Crime> crimes) {
            mCrimes = crimes;
        }

        @Override
        public CrimeHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            View view = layoutInflater.inflate(R.layout.list_item_crime, parent, false);
            return new CrimeHolder(view);
        }

        @Override
        public void onBindViewHolder(CrimeHolder holder, int position) {
            Crime crime = mCrimes.get(position);
            holder.bindCrime(crime);
        }

        @Override
        public int getItemCount() {
            return mCrimes.size();
        }

        public void setCrimes(List<Crime> crimes) {
            mCrimes = crimes;
        }
    }
}
