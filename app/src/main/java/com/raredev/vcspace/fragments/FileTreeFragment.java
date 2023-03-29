package com.raredev.vcspace.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.fragment.app.Fragment;
import androidx.transition.ChangeBounds;
import androidx.transition.TransitionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.raredev.vcspace.R;
import com.raredev.vcspace.actions.Action;
import com.raredev.vcspace.actions.ActionData;
import com.raredev.vcspace.actions.ActionManager;
import com.raredev.vcspace.activity.MainActivity;
import com.raredev.vcspace.databinding.FragmentTreeViewBinding;
import com.raredev.vcspace.events.FileEvent;
import com.raredev.vcspace.managers.SettingsManager;
import com.raredev.vcspace.task.TaskExecutor;
import com.raredev.vcspace.ui.tree.holder.FileViewHolder;
import com.raredev.vcspace.util.ApkInstaller;
import com.raredev.vcspace.util.DialogUtils;
import com.raredev.vcspace.util.FileUtil;
import com.raredev.vcspace.util.ILogger;
import com.raredev.vcspace.util.PreferencesUtils;
import com.raredev.vcspace.util.ViewUtils;
import com.unnamed.b.atv.model.TreeNode;
import com.unnamed.b.atv.view.AndroidTreeView;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.greenrobot.eventbus.EventBus;

public class FileTreeFragment extends Fragment
    implements TreeNode.TreeNodeClickListener, TreeNode.TreeNodeLongClickListener {
  private final String LOG_TAG = FileTreeFragment.class.getSimpleName();
  private static final String KEY_STORED_TREE_STATE = "treeState";
  private FragmentTreeViewBinding binding;
  private AndroidTreeView mTreeView;
  private String mTreeState;

  private TreeNode mRoot;

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    binding = FragmentTreeViewBinding.inflate(inflater, container, false);

    binding.expandCollapse.setOnClickListener(v -> expandCollapseView());
    binding.expandCollapse.setOnLongClickListener(v -> onLongClick(mRoot, null));
    binding.openFolder.setOnClickListener(
        v -> {
          ((ToolsFragment) getParentFragment())
              .mStartForResult.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE));
        });
    binding.openRecent.setOnClickListener(v -> tryOpenRecentFolder());
    binding.refresh.setOnClickListener(v -> refresh());

    binding.close.setOnClickListener(
        v -> {
          new MaterialAlertDialogBuilder(requireContext())
              .setTitle(R.string.close_folder_title)
              .setMessage(R.string.close_folder_message)
              .setPositiveButton(R.string.close, (di, which) -> doCloseFolder(true))
              .setNegativeButton(R.string.cancel, (di, which) -> di.dismiss())
              .show();
        });

    TooltipCompat.setTooltipText(binding.refresh, getString(R.string.refresh));
    TooltipCompat.setTooltipText(binding.close, getString(R.string.close));
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(View v, Bundle savedInstanceState) {
    super.onViewCreated(v, savedInstanceState);
    if (savedInstanceState != null) {
      mTreeState = savedInstanceState.getString(KEY_STORED_TREE_STATE, null);
    }

    if (PreferencesUtils.useOpenRecentsAutomatically()) {
      tryOpenRecentFolder();
    }
  }

  @Override
  public boolean onLongClick(TreeNode node, Object value) {
    if (node == null) {
      return false;
    }
    ActionData data = new ActionData();
    data.put(FileTreeFragment.class, FileTreeFragment.this);
    data.put(TreeNode.class, node);

    ActionManager.getInstance()
        .fillDialogMenu(getChildFragmentManager(), data, Action.Location.FILE_TREE);
    return true;
  }

  @Override
  public void onClick(TreeNode node, Object value) {
    File file = (File) value;

    if (file.isFile()) {
      if (file.getName().endsWith(".apk")) {
        ApkInstaller.installApplication(requireContext(), file);
        return;
      }

      if (FileUtil.isValidTextFile(file.getName())) {
        ((MainActivity) requireActivity()).openFile(file);
      }
    } else {
      if (node.isExpanded()) {
        collapseNode(node);
        return;
      }
      setLoading(node, true);
      listNode(
          node,
          () -> {
            setLoading(node, false);
            expandNode(node);
          });
    }
  }

  public void updateRootNode(File newFile) {
    if (mRoot == null) {
      return;
    }
    if (newFile == null) {
      doCloseFolder(true);
      return;
    }
    loadTreeView(newFile);
  }

  public void tryOpenRecentFolder() {
    try {
      String recentFolderPath =
          PreferencesUtils.getToolsPrefs().getString(SettingsManager.KEY_RECENT_FOLDER, null);
      if (recentFolderPath != null) {
        File recentFolder = new File(recentFolderPath);
        if (recentFolder.exists() && recentFolder.isDirectory()) {
          loadTreeView(new File(recentFolderPath));
        }
      }
    } catch (Throwable e) {
      ILogger.error(LOG_TAG, Log.getStackTraceString(e));
      DialogUtils.newErrorDialog(
          requireContext(),
          getString(R.string.error),
          getString(R.string.error_treeview_opening_recent_files) + "\n\n" + e.toString());
    }
  }

  public void loadTreeView(File rootFolder) {
    if (getContext() == null) {
      return;
    }
    doCloseFolder(false);
    mRoot = TreeNode.root(rootFolder);
    mRoot.setViewHolder(new FileViewHolder(requireContext()));

    listNode(
        mRoot,
        () -> {
          mTreeView = new AndroidTreeView(requireContext(), mRoot, R.drawable.ripple_effect);
          mTreeView.setUseAutoToggle(false);
          mTreeView.setDefaultNodeClickListener(this);
          mTreeView.setDefaultNodeLongClickListener(this);

          if (mTreeView != null) {
            var view = mTreeView.getView();

            binding.horizontalScroll.addView(view);

            EventBus.getDefault().post(new FileEvent(rootFolder));
            tryRestoreSavedState();
          }
        });
    ILogger.info(LOG_TAG, "Opened folder: " + rootFolder.toString());
    updateViewsVisibility();
  }

  public void doCloseFolder(boolean removePrefsAndTreeState) {
    if (mRoot != null) {
      binding.horizontalScroll.removeAllViews();
      mRoot = null;
      mTreeView = null;

      if (removePrefsAndTreeState) {
        PreferencesUtils.getToolsPrefs()
            .edit()
            .putString(SettingsManager.KEY_RECENT_FOLDER, null)
            .apply();
        mTreeState = null;
      }
      EventBus.getDefault().post(new FileEvent(null));
      updateViewsVisibility();
    }
  }

  public void addNewChild(TreeNode parent, File file) {
    TreeNode newNode = new TreeNode(file);
    newNode.setViewHolder(new FileViewHolder(requireContext()));
    parent.addChild(newNode);
  }

  public void listNode(TreeNode node, Runnable post) {
    node.getChildren().clear();
    node.setExpanded(false);
    TaskExecutor.executeAsync(
        () -> {
          listFilesForNode(node);
          var temp = node;

          while (temp.size() == 1) {
            temp = temp.childAt(0);
            if (!temp.getValue().isDirectory()) {
              break;
            }
            listFilesForNode(temp);
            temp.setExpanded(true);
          }
          return null;
        },
        (result) -> {
          post.run();
        });
  }

  public void listFilesForNode(TreeNode parent) {
    File[] files = parent.getValue().listFiles();
    if (files != null) {
      Arrays.sort(files, new FileUtil.SortFileName());
      Arrays.sort(files, new FileUtil.SortFolder());
      for (File file : files) {
        TreeNode child = new TreeNode(file);
        child.setViewHolder(new FileViewHolder(requireContext()));
        parent.addChild(child);
      }
    }
  }

  public void expandNode(TreeNode node) {
    if (mTreeView == null) {
      return;
    }
    TransitionManager.beginDelayedTransition(binding.expandableLayout, new ChangeBounds());
    mTreeView.expandNode(node);
    updateToggle(node);
  }

  public void collapseNode(TreeNode node) {
    if (mTreeView == null) {
      return;
    }
    TransitionManager.beginDelayedTransition(binding.expandableLayout, new ChangeBounds());
    node.getChildren().clear();
    mTreeView.collapseNode(node);
    updateToggle(node);
  }

  private void setLoading(TreeNode node, boolean loading) {
    if (node.getViewHolder() instanceof FileViewHolder) {
      ((FileViewHolder) node.getViewHolder()).setLoading(loading);
    }
  }

  private void updateToggle(TreeNode node) {
    if (node.getViewHolder() instanceof FileViewHolder) {
      ((FileViewHolder) node.getViewHolder()).rotateChevron(node.isExpanded());
    }
  }

  private void tryRestoreSavedState() {
    if (mTreeState != null) {
      mTreeView.collapseAll();
      String[] openNodes = mTreeState.split(AndroidTreeView.NODES_PATH_SEPARATOR);
      restoreNodeState(mRoot, new HashSet<>(Arrays.asList(openNodes)));
    }
  }

  private void restoreNodeState(TreeNode node, Set<String> openNodes) {
    for (TreeNode child : node.getChildren()) {
      if (openNodes.contains(child.getPath())) {
        listNode(
            child,
            () -> {
              expandNode(child);
              restoreNodeState(child, openNodes);
            });
      }
    }
  }

  public void refresh() {
    if (mTreeView != null) {
      mTreeState = mTreeView.getSaveState();
      loadTreeView(mRoot.getValue());
    }
  }

  private void expandCollapseView() {
    if (ViewUtils.isExpanded(binding.expandableLayout)) {
      ViewUtils.collapse(binding.expandableLayout);
      ViewUtils.rotateChevron(false, binding.downButton);
    } else {
      ViewUtils.expand(binding.expandableLayout);
      ViewUtils.rotateChevron(true, binding.downButton);
    }
    updateViewsVisibility();
  }

  private void updateViewsVisibility() {
    if (mRoot == null) {
      binding.folderName.setText(R.string.no_folder_opened);
      binding.expandableLayout.setDisplayedChild(0);
    } else {
      binding.folderName.setText(mRoot.getValue().getName());
      binding.expandableLayout.setDisplayedChild(1);
    }
    binding.folderOptions.setVisibility(mRoot == null ? View.GONE : View.VISIBLE);
  }

  public AndroidTreeView getTreeView() {
    return mTreeView;
  }
}