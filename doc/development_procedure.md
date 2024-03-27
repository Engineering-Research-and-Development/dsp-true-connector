# Development procedures

Development for new connector, based on Dataspace Protocol, will be based on Scrum like approach. To do this following guidelines are defined and will be required to follow it.

[Project dashboard](https://github.com/users/Engineering-Research-and-Development/projects/2)
Currently this project is private so you will have to request access to it. 

This dashboard is used to keep track of backlog, current set of tasks that will be addressed in "sprint", tasks that are in progress, reviewing and that are done.

 - When new ticket is created, it should be in Backlog column.
 - Once ticket is estimated, refined and there is description and scope of the task defined, it can be put into Todo column. Estimation should be set from 8 to max 16 hours. If estimation is more than 16 hours, then consider to split task into multiple smaller tasks. Usually if task is bigger than 16 hours means that it is not understand correctly or that scope is bigger than initially set.
 - Make sure to include testing (junit/integration/GHA/manual) and documentation update in estimation
 - Some general rules for describing tasks: why this task is created, what it solves, possible solution or ideas how to solve it, some pseudo code, links to some pages that can be used as starting point; if it is a bug, steps to reproduce are required
 - Pull request review should not be put aside; each day, if there are pending PR's, some time should be dedicated to addressing PR's
 - If during development of current task something is noticed, that require developers attention (effort to fix/implement) and that cannot be done within the scope of the current task, create new task in Backlog, add description that will be used to do estimation.
 - Be sure NOT TO extend the scope of the current task. This will lead to estimation break an impact development process.
 - Tasks can have dependency on other tasks (those dependencies MUST be linked in the description), be sure to notice those dependencies and not work on dependent tasks in same time frame
 - Output of the task should be:
    * value added through code implementation, test coverage or documentation update
    * new task, that can be of first type
 
## Definition of ready (DoR)

Task can be considered *READY* when following criteria's are fulfilled:
 
 - task breakdown should be present, with clear guideline what needs to be done
 - estimation is present (preferably for each step); be sure not to estimate more than max 16 hours
 - uncertainty is reduced to minimal possible measure (meaning that by reading of the description, anyone from the team can pick up task and start working on it)
 - if from working on DoR some impediments emerge, new Spike (investigation) task should be created and addressed BEFORE work on current task starts. Purpose for Spike task is to reduce level of uncertainty, check or investigate possible solutions, do PoC. Spike task should be treated like any other task.

## Definition of done (DoD)

Task can be considered *DONE* when following criteria's are fulfilled:
 
 - code is implemented and pushed to the GitHub repository (feature branch)
 - new feature or bug fix needs to be covered with junit/integration/GitHub Action tests
 - all tests are pass
 - documentation is updated
 - changelog is updated (if applicable)
 - pull request review is done (resolved all conversation comments from RP) -> code is squash merged to develop branch (one commit to develop branch)
 - feature branch deleted
 - task in Project Dashboard is closed (should be done automatically if linked with branch); if not, close it manually

## Working on task

 - When task is compliant with DoR, ticket in Project Dashboard should be moved to In Progress column, and assigned to the developer.
 - Convert task to the issue. This will create issue in GitHub project Issues tab.
 - New feature branch should be created, with name clear enough to know which task that is; be sure to synchronize local develop branch before creating feature branch from it. This will minimize later synchronization problems.
 - Once branch is pushed to remote, link branch with ticket in Project Backlog. This can be done when editing task, and in Development section select branch created.
 - When development is done, and all criteria from ticket are implemented, create pull request and assign developers from review
 - Once pull request is approved, merge code to develop; if task and branch are linked, once PR is merged it should move ticket to Done.
 - Use DoD as guideline for completing the task
 

