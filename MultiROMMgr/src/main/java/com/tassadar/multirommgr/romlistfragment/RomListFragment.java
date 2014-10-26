/*
 * This file is part of MultiROM Manager.
 *
 * MultiROM Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MultiROM Manager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MultiROM Manager. If not, see <http://www.gnu.org/licenses/>.
 */

package com.tassadar.multirommgr.romlistfragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.tassadar.multirommgr.MainFragment;
import com.tassadar.multirommgr.MultiROMSwipeRefreshLayout;
import com.tassadar.multirommgr.R;
import com.tassadar.multirommgr.Rom;
import com.tassadar.multirommgr.StatusAsyncTask;


public class RomListFragment extends MainFragment implements AdapterView.OnItemClickListener, RomListItem.OnRomActionListener, MultiROMSwipeRefreshLayout.ScrollUpListener {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        m_view = inflater.inflate(R.layout.fragment_rom_list, container, false);

        m_romList = (ListView)findViewById(R.id.rom_list);
        m_adapter = new RomListAdapter(getActivity(), this);

        m_romList.setEmptyView(findViewById(R.id.rom_list_empty_text));
        m_romList.setAdapter(m_adapter);
        m_romList.setOnItemClickListener(this);

        m_actListener.addScrollUpListener(this);
        m_actListener.onFragmentViewCreated();
        return m_view;
    }

    public void invalidateAdapter() {
        if(m_adapter != null)
            m_adapter.setChanged();
    }

    private void setRefreshing(boolean refreshing) {
        View p = findViewById(R.id.progress_bar);
        p.setVisibility(refreshing ? View.VISIBLE : View.GONE);

        m_romList.setVisibility(refreshing ? View.GONE : View.VISIBLE);
        m_romList.getEmptyView()
                .setVisibility((!refreshing && !m_adapter.isEmpty()) ? View.VISIBLE : View.GONE);
    }

    @Override
    public void startRefresh() {
        super.startRefresh();
        setRefreshing(true);
    }

    @Override
    public void onStatusTaskFinished(StatusAsyncTask.Result res) {
        setRefreshing(false);

        if(res.multirom != null) {
            m_adapter.set(res.multirom.getRoms());
        } else {
            m_adapter.clear();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int pos, long id) {
        Bundle b = new Bundle();
        Rom rom = m_adapter.getItem(pos);
        b.putParcelable("rom", rom);

        RomBootDialog d = new RomBootDialog();
        d.setArguments(b);
        d.show(getFragmentManager(), "RomBootFragment");
    }

    @Override
    public void onRenameClicked(Rom rom) {
        Bundle b = new Bundle();
        b.putParcelable("rom", rom);

        RomRenameDialog d = new RomRenameDialog();
        d.setArguments(b);
        d.show(getFragmentManager(), "RomRenameFragment");
    }

    @Override
    public void onEraseClicked(Rom rom) {
        Bundle b = new Bundle();
        b.putParcelable("rom", rom);

        RomEraseDialog d = new RomEraseDialog();
        d.setArguments(b);
        d.show(getFragmentManager(), "RomEraseFragment");
    }

    @Override
    public void onIconClicked(Rom rom) {
        Bundle b = new Bundle();
        b.putParcelable("rom", rom);

        RomIconDialog d = new RomIconDialog();
        d.setArguments(b);
        d.show(getFragmentManager(), "RomPredefIconFragment");
    }

    @Override
    public boolean canChildScrollUp() {
        return canChildScrollUp(m_romList);
    }

    private ListView m_romList;
    private RomListAdapter m_adapter;
}
