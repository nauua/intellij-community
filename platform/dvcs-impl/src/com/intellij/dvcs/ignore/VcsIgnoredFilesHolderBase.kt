// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore

import com.intellij.dvcs.repo.AbstractRepositoryManager
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.FileHolder
import com.intellij.openapi.vcs.changes.VcsIgnoredFilesHolder
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EventDispatcher
import com.intellij.vcsUtil.VcsUtil

abstract class VcsIgnoredFilesHolderBase<REPOSITORY : Repository>(
  private val project: Project,
  private val repositoryManager: AbstractRepositoryManager<REPOSITORY>
) : VcsIgnoredFilesHolder {

  private val listeners = EventDispatcher.create(VcsIgnoredHolderUpdateListener::class.java)

  private val vcsIgnoredHolderMap =
    repositoryManager.repositories.associateTo(
      hashMapOf<REPOSITORY, VcsRepositoryIgnoredFilesHolder>()) { it to getHolder(it) }

  protected abstract fun getHolder(repository: REPOSITORY): VcsRepositoryIgnoredFilesHolder

  override fun getType() = FileHolder.HolderType.IGNORED

  override fun isInUpdatingMode() = vcsIgnoredHolderMap.values.any(VcsRepositoryIgnoredFilesHolder::isInUpdateMode)

  override fun notifyVcsStarted(vcs: AbstractVcs<*>?) {}

  override fun cleanAndAdjustScope(scope: VcsModifiableDirtyScope) {}

  override fun addFile(file: VirtualFile) {
    findIgnoreHolderByFile(file)?.addFile(file)
  }

  override fun containsFile(file: VirtualFile) = findIgnoreHolderByFile(file)?.containsFile(file) ?: false

  override fun values() = vcsIgnoredHolderMap.flatMap { it.value.ignoredFiles }

  override fun startRescan() {
    fireUpdateStarted()
    vcsIgnoredHolderMap.values.forEach { it.startRescan(null) }
    fireUpdateFinished()
  }

  override fun cleanAll() {
    vcsIgnoredHolderMap.clear()
  }

  private fun findIgnoreHolderByFile(file: VirtualFile): VcsRepositoryIgnoredFilesHolder? =
    VcsUtil.getVcsRootFor(project, file)?.let { repositoryRoot ->
      repositoryManager.getRepositoryForRoot(repositoryRoot)?.let { repositoryForRoot ->
        vcsIgnoredHolderMap[repositoryForRoot]
      }
    }

  private fun fireUpdateStarted() {
    listeners.multicaster.updateStarted()
  }

  private fun fireUpdateFinished() {
    listeners.multicaster.updateFinished()
  }
}