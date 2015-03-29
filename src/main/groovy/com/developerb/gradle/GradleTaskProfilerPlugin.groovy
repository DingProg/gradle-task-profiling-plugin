package com.developerb.gradle

import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

class GradleTaskProfilerPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        final Watch watch = new Watch()
        final Map<String, Tracked> tracked = [:]

        final taskListener = new TaskExecutionListener() {

            @Override
            void beforeExecute(Task task) {
                tracked[task.path] = new Tracked(task, watch.stamp())
            }

            @Override
            void afterExecute(Task task, TaskState taskState) {
                tracked[task.path].finished(watch.stamp())
            }

        }

        final buildListener = new BuildAdapter() {

            @Override
            void buildFinished(BuildResult result) {
                def prettyJson = prettyPrint(toJson([
                        metadata: [
                                project: project.name,
                                description: project.description,
                                timestamp: System.currentTimeMillis(),
                                directory: project.gradle.startParameter.currentDir.absolutePath,
                                startParameterTaskNames: project.gradle.startParameter.taskNames
                        ],
                        tasks: tracked.values()
                ]))

                def reportsDirectory = new File(project.buildDir, "reports")
                def reportDirectory = new File(reportsDirectory, "task-profile-report")
                reportDirectory.mkdirs()

                new File(reportDirectory, "profile-data.json").write(prettyJson, "utf-8")
                new File(reportDirectory, "index.html").write(GradleTaskProfilerPlugin.getResource("/assets/index.html").getText("utf-8"), "utf-8")
                new File(reportDirectory, "app.js").write(GradleTaskProfilerPlugin.getResource("/assets/app.js").getText("utf-8"), "utf-8")
            }

        }

        project.gradle.addListener(taskListener)
        project.gradle.addBuildListener(buildListener)
    }


    static class Watch {

        final long started = System.currentTimeMillis()

        long stamp() {
            System.currentTimeMillis() - started
        }

    }

    static class Tracked {

        String name, path, group, description
        String threadName

        long started
        long finished

        List<String> dependsOn = []

        Tracked(Task task, long startedAt) {
            name = task.name
            path = task.path
            group = task.group
            description = task.description
            threadName = Thread.currentThread().name
            started = startedAt
            dependsOn = task.taskDependencies.getDependencies(task).collect { it.path }
        }

        void finished(long finishedAt) {
            finished = finishedAt
        }

    }


}
