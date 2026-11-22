# Todo frontend (CAP-1)

A browser UI for the CAP-2 Todo backend: list, add, edit, mark Completed or Pending, and delete tasks. Plain HTML, CSS
and native ES modules, with no build step, no npm and no dependency. A small single-file Java program
(`server/FrontendServer.java`) serves the page and forwards `/todos` to the backend, so the page and the API share one
origin and no CORS headers are needed (the backend sends none).

Status labels used below:
- "run" means an agent ran the command in this repository and saw it succeed (evidence is in
  `docs/sdlc/CAP-1/implementation-report.md`).
- "not run" means the command is documented from the code or from `backend/README.md` but nobody has run it yet.
- Browser results are only ever reported by a human. No agent has executed any of the JavaScript in this directory.

## Working directory rule

Run the frontend commands from this directory (`frontend/`); this README uses that form. The server looks for the web
root as `./public` and then `./frontend/public`, relative to the working directory, so it also starts from the
repository root with a `frontend/` prefix, for example `java frontend/server/FrontendServer.java` (checked by the test
case "server started from the repository root finds frontend/public", run; the server was not started by hand). If
neither location contains `index.html` it refuses to start (exit code 1, one-line message naming both locations).

You need JDK 21 or newer (the server uses virtual threads). `java -version` printed `java version "25.0.5.1"` on the
developer machine (run). Nothing else is installed or downloaded.

## 1. Start the backend first

From `backend/` (see `backend/README.md`, "Run" and "Working directory rule"):

```
./mvnw spring-boot:run
```

(not run: `backend/README.md` lists `./mvnw spring-boot:run` as "unverified (never run)"; it needs Maven Central the
first time.) The backend listens on `127.0.0.1:8080`. The verified way to start it is the packaged JAR described in
`backend/README.md` ("Run"). Leave this window open.

## 2. Start the frontend server

From `frontend/`, in a second window (not run standalone by an agent: it holds port 8000 until stopped):

```
java server/FrontendServer.java
```

Options: `--port=<n>` (default 8000) and `--backend-port=<n>` (default 8080), for example
`java server/FrontendServer.java --port=8001 --backend-port=8081`. It prints one line,
`Frontend at http://127.0.0.1:<port>/ - proxying /todos to http://127.0.0.1:<backend-port>`, binds to `127.0.0.1` only,
writes no file, and stops with Ctrl+C. If the port is in use it exits non-zero and names the port and `--port`.

If `java` is not on your `PATH`, use `JAVA_HOME` (both forms not run):

- Git Bash: `"$JAVA_HOME/bin/java" server/FrontendServer.java`
- cmd: `"%JAVA_HOME%\bin\java" server\FrontendServer.java`

Then open **http://127.0.0.1:8000/** (use this address, not the file on disk: modules and the `/todos` proxy do not
work from `file://`; a note on the page says so until the app has started).

## 3. Browser self-test page (about 2 minutes)

With the frontend server running, open **http://127.0.0.1:8000/tests/**. It runs every UI test in the browser against
fake responses (no backend is needed for it) and prints a line such as `N passed, M failed`, one line per test, and a
`Paste block` (`<pre id="results">`). Please send back:

1. the `N passed, M failed` line, verbatim;
2. the whole `Paste block` text when anything failed (it lists each failed test and your browser);
3. the browser name and version.

- [ ] Summary line pasted: `______ passed, ______ failed`
- [ ] Browser and version: `______`

## 4. Manual checklist against the real backend (about 10 minutes)

Run with both processes up (steps 1 and 2). Open the browser developer console (F12, Console tab) first and keep it
visible. Tick each box and write down pass or fail; anything you do not do stays NOT_RUN.

**Browser and console**
- [ ] Browser and version used: `______` (the support statement is below; tick one row per browser you use)
- [ ] Console: no CORS error at any point of the flow below. A red 404 for another file is not a CORS error; this
  page declares a `data:` icon so the browser should not request `/favicon.ico`, and if you see a favicon 404 anyway it
  is unrelated to CORS and to `/todos`. A CORS error would name `Access-Control-Allow-Origin` or "blocked by CORS policy".

**AC-23 real-backend flow** (same origin, list/add/edit/status/delete work)
- [ ] The page at http://127.0.0.1:8000/ shows "Loading tasks..." and then the existing tasks (or "No tasks yet").
- [ ] Add: type a title (and optionally a description), press "Add task". The new task appears at the top as Pending,
  the form is cleared and "Task added" is shown.
- [ ] Edit: press Edit on a task, change the title, press Save. The row shows the new values and "Task updated" is shown.
  Also check that Cancel (and the Escape key) leaves the task unchanged.
- [ ] Mark completed: press "Mark as completed" on a Pending task. A "Completed" badge (text, not only a colour or a
  strike-through) appears, the button now reads "Mark as pending", and "Task marked as completed" is shown. Press it
  again: back to Pending and "Task marked as pending" is shown.
- [ ] Delete: press Delete. A confirmation dialog appears and names the task. Cancel leaves the task in place and sends
  nothing; Delete removes the task and shows "Task deleted".
- [ ] AC-21 with a real task: add a task whose title is `<b>x</b><script>1</script>` and whose description has two lines.
  It is displayed exactly as typed, as plain text (no bold, no script), with the line break kept in the description.
  (A functional display check only; see "What is not verified".)
- [ ] In the Network tab the requests go to `http://127.0.0.1:8000/todos...` (the same origin as the page).
- [ ] `git diff --stat -- backend/` run from the repository root prints nothing (the backend is unchanged).

**AC-3 load failure and Retry**
- [ ] Stop the backend (Ctrl+C in its window), keep the frontend server running, and reload http://127.0.0.1:8000/.
  An error message ("Something went wrong. Please try again.") and a Retry button are shown, and the page does not say
  "No tasks yet".
- [ ] Start the backend again (step 1), wait until it is up, and press Retry. The task list is displayed.

**Keyboard and screen reader**
- [ ] Enter on an empty title: click into the Title field, leave it empty, press Enter. "Title is required" is shown
  beside the field and in the message area at the top, and focus is on the Title field. With a screen reader the
  message is spoken once (best effort).
- [ ] Keyboard only (no mouse): Tab reaches the title, description, "Add task", and each task's Edit, "Mark as
  completed" or "Mark as pending", and Delete, in a sensible order; the focused control always has a visible outline; you can add, edit, save, mark
  completed and delete a task with Tab, Shift+Tab, Enter and Space only.
- [ ] Delete confirmation: when the dialog opens, focus is on Cancel; Tab and Shift+Tab stay inside the dialog (they
  wrap between Cancel and Delete and never reach the page behind it); Escape closes it without deleting; after it
  closes, focus returns to a sensible control on the page.
- [ ] Screen reader (best effort, optional): success and error messages are announced when they appear. Name the
  screen reader: `______`

**Record**
- [ ] Anything that failed, with the exact text or a screenshot description: `______`

## What is not verified

- **No security, speed or performance testing** (decision H-2). Not run, and nothing was designed for it: the 10-second
  request limit as a timing claim, load, response times, hostile or malformed input against the server, path traversal,
  and authentication. The server only serves files below `public/` and forwards `/todos` unchanged; that is a design
  statement, not a test result.
- **The JavaScript has never been executed by an agent.** Every UI behaviour (AC-1 to AC-22) is verified only by your
  browser run of section 3 and checklist 4. Until you paste the results they are NOT_RUN.
- The only functional check an agent ran is the proxy and static-server test, from the repository root:
  `java frontend/server/FrontendServerTest.java` (run; it starts the server as a child process against an in-process
  stub backend and takes no arguments). From `frontend/` the command is `java server/FrontendServerTest.java` (the
  file's header documents it; the latest agent ran only the repository-root form; the `JAVA_HOME` forms for it,
  `"$JAVA_HOME/bin/java" server/FrontendServerTest.java` in Git Bash and `"%JAVA_HOME%\bin\java"
  server\FrontendServerTest.java` in cmd, were not run). Its result is in the implementation report. It uses a stub, not
  the real backend, and it covers only the Java server, not any browser code. One of its cases is reported as SKIPPED
  by design (see the report).
- **Malformed percent sequences.** For a path such as `/todos/%zz` the Tomcat in the backend answers with its own
  `400 text/html` page, and this server passes it through unchanged; the UI then shows the generic error. How the
  frontend server itself treats a malformed percent sequence in a static path was not exercised.
- **Browser support statement.** The target is the current desktop Chrome, Edge, Firefox and Safari, latest two
  versions each, with no Internet Explorer and no mobile layout. Only the browsers you list in the checklist were
  used; for every other browser the result is not verified. Accessibility is a best-effort aim at WCAG 2.1 AA and is
  not certified.
- **The real Escape key and native `<dialog>` behaviour** differ slightly between browsers; they are covered only by
  the checklist items above.
- **Two processes.** The frontend and the backend are two separate programs you start and stop yourself, in that
  order. If the backend is not running, the server answers 502 for `/todos` and the page shows the load-failure
  message with Retry.
- **No authentication, loopback only.** Anyone who can reach port 8000 on this machine can use and change the tasks.
  The server binds to `127.0.0.1` only, so other machines cannot connect; listening on other addresses is not
  supported and was not tested.
