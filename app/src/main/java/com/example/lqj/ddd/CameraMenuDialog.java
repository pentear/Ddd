package com.example.lqj.ddd;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * 照片界面中的弹出菜单（对话框）
 */
public class CameraMenuDialog extends AlertDialog.Builder {
    private Context mContext = null;
    private AlertDialog mDlg = null;
    private ArrayList<MenuItem> mListData = null;
    private OnMenuClickListener mOnMenuClickListener = null;
    private ListView mListView = null;

    public CameraMenuDialog(Context context) {
        super(context);
        mContext = context;
        mListView = new ListView(context);
        mListData = new ArrayList<MenuItem>();
        setView(mListView);
    }

    public void addMenu(int menuId, String menuName) {
        mListData.add(new MenuItem(menuId, menuName));
    }

    public void addMenu(int menuId, String menuName, Object tag) {
        mListData.add(new MenuItem(menuId, menuName, tag));
    }

    @Override
    public AlertDialog create() {
        if (mDlg == null) {
            mListView.setAdapter(new MenuAdapter(mContext, mListData));
            mListView.setOnItemClickListener(new OnMenuItemClickListener(mListData));
            mDlg = super.create();
        }
        return mDlg;
    }

    /**
     * 点击菜单项的事件（需要重载后实现）
     *
     * @param menuId   菜单ID
     * @param menuName 菜单项名称
     * @param tag      菜单项Tag
     * @param view     菜单项View
     */
    protected void onMenuClick(int menuId, String menuName, Object tag, View view) {
    }

    public void setOnMenuClickListener(OnMenuClickListener onMenuClickListener) {
        mOnMenuClickListener = onMenuClickListener;
    }

    public interface OnMenuClickListener {
        /**
         * 点击菜单项的事件（需要重载后实现）
         *
         * @param menuId   菜单ID
         * @param menuName 菜单项名称
         * @param tag      菜单项Tag
         * @param view     菜单项View
         */
        public void onMenuClick(int menuId, String menuName, Object tag,
                                View view);
    }

    private class OnMenuItemClickListener implements AdapterView.OnItemClickListener {
        private ArrayList<MenuItem> data;

        public OnMenuItemClickListener(ArrayList<MenuItem> data) {
            this.data = data;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                                long id) {
            MenuItem item = (MenuItem) data.get(position);
            if (mOnMenuClickListener != null) {
                mOnMenuClickListener.onMenuClick(item.getMenuId(),
                        item.getMenuName(), item.getTag(), view);
            } else {
                CameraMenuDialog.this.onMenuClick(item.getMenuId(),
                        item.getMenuName(), item.getTag(), view);
            }
            mDlg.dismiss();
        }
    }

    class MenuAdapter extends BaseAdapter {
        private ArrayList<MenuItem> data;
        private Context context;

        public MenuAdapter(Context context, ArrayList<MenuItem> data) {
            this.context = context;
            this.data = data;
        }

        @Override
        public int getCount() {
            if (data != null) {
                return data.size();
            }
            return 0;
        }

        @Override
        public Object getItem(int position) {
            if (getCount() > position) {
                return data.get(position);
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            if (getCount() > position) {
                return position;
            }
            return -1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(context);
                convertView = inflater.inflate(android.R.layout.simple_list_item_1,
                        parent, false);
            }
            TextView text = (TextView) convertView.findViewById(android.R.id.text1);
            text.setGravity(Gravity.CENTER);
            text.setText(((MenuItem) data.get(position)).getMenuName().trim());
            return convertView;
        }
    }

    class MenuItem {
        private int menuId;
        private String menuName;
        private Object tag = null;

        public MenuItem(int menuId, String menuName) {
            this.menuId = menuId;
            this.menuName = menuName;
        }

        public MenuItem(int menuId, String menuName, Object tag) {
            this.menuId = menuId;
            this.menuName = menuName;
            this.tag = tag;
        }

        /**
         * 菜单标签
         */
        public Object getTag() {
            return tag;
        }

        /**
         * 菜单标签
         */
        public void setTag(Object tag) {
            this.tag = tag;
        }

        /**
         * 菜单ID
         */
        public int getMenuId() {
            return menuId;
        }

        /**
         * 菜单ID
         */
        public void setMenuId(int menuId) {
            this.menuId = menuId;
        }

        /**
         * 菜单显示的名称
         */
        public String getMenuName() {
            return menuName;
        }

        /**
         * 菜单显示的名称
         */
        public void setMenuName(String menuName) {
            this.menuName = menuName;
        }
    }
}
