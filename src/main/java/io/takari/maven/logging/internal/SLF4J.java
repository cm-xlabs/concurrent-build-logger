/*
 * Copyright (c) 2015-2016 salesforce.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.logging.internal;


import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.Lifecycle;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.slf4j.MDC;

import ch.qos.logback.classic.spi.ILoggingEvent;

public final class SLF4J {
  private SLF4J() {}

  public interface LifecycleListener {
    default void onSessionStart(MavenSession session) {}

    default void onSessionFinish(MavenSession session) {}

    default void onProjectBuildStart(MavenProject project) {}

    default void onProjectBuildFinish(MavenProject project) {}

    default void onMojoExecutionStart(MavenProject project, Lifecycle lifecycle,
        MojoExecution execution) {}
  }

  public static final String KEY_PROJECT_ID = "maven.project.id";

  public static final String KEY_PROJECT_GROUPID = "maven.project.groupId";

  public static final String KEY_PROJECT_ARTIFACTID = "maven.project.artifactId";

  public static final String KEY_PROJECT_VERSION = "maven.project.version";

  public static final String KEY_PROJECT_BASEDIR = "maven.project.basedir";

  public static final String KEY_PROJECT_LOGDIR = "maven.project.logdir";

  public static final String KEY_MOJO_ID = "maven.mojo.id";

  public static final String KEY_MOJO_GROUPID = "maven.mojo.groupId";

  public static final String KEY_MOJO_ARTIFACTID = "maven.mojo.artifactId";

  public static final String KEY_MOJO_VERSION = "maven.mojo.version";

  public static final String KEY_MOJO_GOAL = "maven.mojo.goal";
  
  private static final ThreadLocal<Map<String, String>> INHERITABLE_MDC = new InheritableThreadLocal<>();

  /**
   * Returns conventional per-project build log directory.
   */
  public static String getLogdir(MavenProject project) {
    return project.getBuild().getDirectory();
  }

  /**
   * Puts specified project to the current thread's diagnostic context.
   * 
   * @see org.apache.maven.lifecycle.internal.builder.Builder
   */
  public static void putMDC(MavenProject project) {
    if (project == null || project.getBasedir() == null || !project.getBasedir().exists()) {
      // ignore standalone mvn execution
      return;
    }
    put(KEY_PROJECT_ID, project.getId());
    put(KEY_PROJECT_GROUPID, project.getGroupId());
    put(KEY_PROJECT_ARTIFACTID, project.getArtifactId());
    put(KEY_PROJECT_BASEDIR, project.getBasedir().getAbsolutePath());
    put(KEY_PROJECT_LOGDIR, getLogdir(project));
  }

  /**
   * Removes project information from the current thread's diagnostic context.
   * 
   * @see org.apache.maven.lifecycle.internal.builder.Builder
   */
  public static void removeMDC(MavenProject project) {
    if (project == null) {
      return;
    }
    remove(KEY_PROJECT_ID);
    remove(KEY_PROJECT_GROUPID);
    remove(KEY_PROJECT_ARTIFACTID);
    remove(KEY_PROJECT_BASEDIR);
    remove(KEY_PROJECT_LOGDIR);
  }

  private static List<LifecycleListener> listeners = new CopyOnWriteArrayList<>();

  public static void addListener(LifecycleListener listener) {
    listeners.add(listener);
  }

  public static void removeListener(LifecycleListener listener) {
    Iterator<LifecycleListener> iterator = listeners.iterator();
    while (iterator.hasNext()) {
      if (iterator.next() == listener) {
        iterator.remove();
      }
    }
  }

  static void notifySessionStart(MavenSession session) {
    for (LifecycleListener listener : listeners) {
      listener.onSessionStart(session);
    }
  }

  static void notifySessionFinish(MavenSession session) {
    for (LifecycleListener listener : listeners) {
      listener.onSessionFinish(session);
    }
  }

  static void notifyProjectBuildStart(MavenProject project) {
    putMDC(project);
    for (LifecycleListener listener : listeners) {
      listener.onProjectBuildStart(project);
    }
  }

  static void notifyProjectBuildFinish(MavenProject project) {
    for (LifecycleListener listener : listeners) {
      listener.onProjectBuildFinish(project);
    }
    removeMDC(project);
  }

  static void notifyMojoExecutionStart(MavenProject project, Lifecycle lifecycle,
      MojoExecution execution) {
    StringBuilder id = new StringBuilder();
    id.append(execution.getGroupId());
    id.append(':');
    id.append(execution.getArtifactId());
    id.append(':');
    id.append(execution.getGoal());
    if (!execution.getExecutionId().equals("default-" + execution.getGoal())) {
      id.append(':');
      id.append(execution.getExecutionId());
    }
    put(KEY_MOJO_ID, id.toString());
    put(KEY_MOJO_GROUPID, execution.getGroupId());
    put(KEY_MOJO_ARTIFACTID, execution.getArtifactId());
    put(KEY_MOJO_VERSION, execution.getVersion());
    put(KEY_MOJO_GOAL, execution.getGoal());
    for (LifecycleListener listener : listeners) {
      listener.onMojoExecutionStart(project, lifecycle, execution);
    }
  }

  static void notifyMojoExecutionFinish(MavenProject project, MojoExecution execution) {
    remove(KEY_MOJO_ID);
    remove(KEY_MOJO_GROUPID);
    remove(KEY_MOJO_ARTIFACTID);
    remove(KEY_MOJO_VERSION);
    remove(KEY_MOJO_GOAL);
  }

  public static String getFromMDC(ILoggingEvent event, String key) {
    String value = event.getMDCPropertyMap().get(key);
    if (value == null) {
      value = getFallback(key);
    }
    return value;
  }
  
  private static String getFallback(String key) {
    if (INHERITABLE_MDC.get() == null) {
      return null;
    }
    return INHERITABLE_MDC.get().get(key);

  }

  private static void put(String key, String value) {
    MDC.put(key, value);
    if (INHERITABLE_MDC.get() == null) {
      INHERITABLE_MDC.set(new HashMap<>());
    }
    INHERITABLE_MDC.get().put(key, value);
  }

  private static void remove(String key) {
    MDC.remove(key);
    INHERITABLE_MDC.get().remove(key);
  }
}
