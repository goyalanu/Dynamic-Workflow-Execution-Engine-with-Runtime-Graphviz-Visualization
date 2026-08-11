package com.example.workflow.service;

public final class LiveDashboardPage {
    public String render() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Workflow Runtime Dashboard</title>
                  <style>
                    :root {
                      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                      background: #f5f7fa;
                      color: #17202a;
                    }
                    body { margin: 0; }
                    header {
                      display: flex;
                      justify-content: space-between;
                      gap: 18px;
                      align-items: center;
                      padding: 22px 28px;
                      background: #ffffff;
                      border-bottom: 1px solid #d8dee7;
                    }
                    h1 { margin: 0; font-size: 22px; }
                    .actions { display: flex; flex-wrap: wrap; gap: 8px; }
                    button {
                      border: 1px solid #afbac8;
                      background: #ffffff;
                      color: #17202a;
                      border-radius: 6px;
                      padding: 9px 12px;
                      font-weight: 650;
                      cursor: pointer;
                    }
                    button.primary { background: #1f6feb; color: #ffffff; border-color: #1f6feb; }
                    main {
                      display: grid;
                      grid-template-columns: 320px minmax(0, 1fr);
                      min-height: calc(100vh - 81px);
                    }
                    aside {
                      border-right: 1px solid #d8dee7;
                      background: #ffffff;
                      padding: 18px;
                    }
                    .content { padding: 18px 22px 28px; }
                    .panel {
                      background: #ffffff;
                      border: 1px solid #d8dee7;
                      border-radius: 8px;
                      overflow: hidden;
                    }
                    .panel h2 {
                      margin: 0;
                      padding: 14px 16px;
                      font-size: 16px;
                      border-bottom: 1px solid #e6eaf0;
                    }
                    .run-list { display: grid; gap: 8px; }
                    .run {
                      width: 100%;
                      text-align: left;
                      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
                      font-size: 12px;
                      overflow-wrap: anywhere;
                    }
                    .summary {
                      display: grid;
                      grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
                      gap: 10px;
                      margin-bottom: 14px;
                    }
                    .metric {
                      background: #ffffff;
                      border: 1px solid #d8dee7;
                      border-radius: 8px;
                      padding: 12px;
                    }
                    .metric b { display: block; font-size: 22px; }
                    .metric span { color: #526170; font-size: 13px; }
                    .graph { padding: 16px; overflow: auto; min-height: 340px; }
                    .graph img { max-width: 100%; height: auto; display: block; }
                    table { width: 100%; border-collapse: collapse; }
                    th, td { padding: 10px 12px; border-bottom: 1px solid #edf0f4; text-align: left; font-size: 14px; }
                    th { color: #526170; font-weight: 650; }
                    .status {
                      display: inline-block;
                      border-radius: 999px;
                      padding: 4px 8px;
                      font-size: 12px;
                      font-weight: 750;
                    }
                    .COMPLETED { background: #d9f6df; color: #17652f; }
                    .RUNNING { background: #dff0ff; color: #075b99; }
                    .PENDING { background: #eef2f7; color: #526170; }
                    .FAILED { background: #ffe3e3; color: #a21d1d; }
                    .BLOCKED { background: #fff1bd; color: #7a5700; }
                    .RETRYING { background: #efe5ff; color: #6741a4; }
                    @media (max-width: 820px) {
                      header { align-items: flex-start; flex-direction: column; }
                      main { grid-template-columns: 1fr; }
                      aside { border-right: 0; border-bottom: 1px solid #d8dee7; }
                    }
                  </style>
                </head>
                <body>
                  <header>
                    <div>
                      <h1>OrderFulfillment Runtime Dashboard</h1>
                      <div id="subtitle">Start a run to watch persisted state become a graph.</div>
                    </div>
                    <div class="actions">
                      <button class="primary" onclick="startRun()">Start Workflow</button>
                    </div>
                  </header>
                  <main>
                    <aside>
                      <h2>Runs</h2>
                      <div id="runs" class="run-list"></div>
                    </aside>
                    <section class="content">
                      <div class="summary" id="summary"></div>
                      <div class="panel">
                        <h2>Live Graphviz SVG</h2>
                        <div class="graph"><img id="graph" alt="Workflow graph"></div>
                      </div>
                      <div style="height:14px"></div>
                      <div class="panel">
                        <h2>Persisted Step State</h2>
                        <table>
                          <thead><tr><th>Step</th><th>Status</th><th>Retries</th><th>Error</th></tr></thead>
                          <tbody id="steps"></tbody>
                        </table>
                      </div>
                    </section>
                  </main>
                  <script>
                    let selectedRunId = null;
                    let timer = null;

                    async function startRun() {
                      const response = await fetch('/api/runs', { method: 'POST' });
                      const body = await response.json();
                      selectedRunId = body.runId;
                      await refreshRuns();
                      refreshState();
                      if (timer) clearInterval(timer);
                      timer = setInterval(refreshState, 1000);
                    }

                    async function refreshRuns() {
                      const response = await fetch('/api/runs');
                      const body = await response.json();
                      const runs = document.getElementById('runs');
                      runs.innerHTML = '';
                      body.runs.forEach(runId => {
                        const button = document.createElement('button');
                        button.className = 'run';
                        button.textContent = runId;
                        button.onclick = () => {
                          selectedRunId = runId;
                          refreshState();
                          if (timer) clearInterval(timer);
                          timer = setInterval(refreshState, 1000);
                        };
                        runs.appendChild(button);
                      });
                    }

                    async function refreshState() {
                      if (!selectedRunId) return;
                      const response = await fetch('/api/runs/' + selectedRunId + '/state');
                      const state = await response.json();
                      document.getElementById('subtitle').textContent = 'Viewing run ' + selectedRunId;
                      document.getElementById('graph').src = '/api/runs/' + selectedRunId + '/graph.svg?ts=' + Date.now();
                      renderSummary(state.counts);
                      renderSteps(state.steps);
                      if (state.complete && timer) {
                        clearInterval(timer);
                        timer = null;
                      }
                    }

                    function renderSummary(counts) {
                      const summary = document.getElementById('summary');
                      summary.innerHTML = '';
                      Object.keys(counts).forEach(status => {
                        const metric = document.createElement('div');
                        metric.className = 'metric';
                        metric.innerHTML = '<b>' + counts[status] + '</b><span>' + status + '</span>';
                        summary.appendChild(metric);
                      });
                    }

                    function renderSteps(steps) {
                      const body = document.getElementById('steps');
                      body.innerHTML = '';
                      steps.forEach(step => {
                        const row = document.createElement('tr');
                        row.innerHTML =
                          '<td>' + step.displayName + '</td>' +
                          '<td><span class="status ' + step.status + '">' + step.status + '</span></td>' +
                          '<td>' + step.retryCount + '/' + step.maxRetries + '</td>' +
                          '<td>' + (step.error || '') + '</td>';
                        body.appendChild(row);
                      });
                    }

                    refreshRuns();
                  </script>
                </body>
                </html>
                """;
    }
}
