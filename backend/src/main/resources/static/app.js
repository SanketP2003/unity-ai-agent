/**
 * Autonomous Unity Game Builder — Web Studio Client
 * Connects directly to Spring Boot AgentService via HTTP + SSE
 */

(() => {
  // DOM Elements - Chat & Run
  const chatMessages = document.getElementById('chat-messages');
  const userPrompt = document.getElementById('user-prompt');
  const btnSend = document.getElementById('btn-send');
  const btnCancel = document.getElementById('btn-cancel');
  const sessionInput = document.getElementById('session-input');
  const btnNewSession = document.getElementById('btn-new-session');
  const welcomeCard = document.getElementById('welcome-card');
  const activityBanner = document.getElementById('agent-activity-banner');
  const activityText = document.getElementById('agent-activity-text');
  const activityRunBadge = document.getElementById('agent-run-badge');

  // DOM Elements - Badges & Banners
  const badgeBackend = document.getElementById('badge-backend');
  const badgeUnity = document.getElementById('badge-unity');
  const badgeProvider = document.getElementById('badge-provider');
  const badgeProviderText = document.getElementById('badge-provider-text');

  const goalBanner = document.getElementById('goal-banner');
  const goalDescription = document.getElementById('goal-description');
  const agentStateBadge = document.getElementById('agent-state-badge');
  const currentPlanStep = document.getElementById('current-plan-step');
  const pipelineBanner = document.getElementById('pipeline-banner');

  const resultsBar = document.getElementById('results-bar');
  const valCompilation = document.getElementById('val-compilation');
  const valRuntime = document.getElementById('val-runtime');
  const valValidation = document.getElementById('val-validation');

  // DOM Elements - Settings Modal
  const btnOpenSettings = document.getElementById('btn-open-settings');
  const btnCloseSettings = document.getElementById('btn-close-settings');
  const btnCancelSettings = document.getElementById('btn-cancel-settings');
  const btnSaveSettings = document.getElementById('btn-save-settings');
  const settingsModal = document.getElementById('settings-modal');
  const cfgProvider = document.getElementById('cfg-provider');
  const cfgBaseUrl = document.getElementById('cfg-base-url');
  const cfgModel = document.getElementById('cfg-model');
  const cfgApiKey = document.getElementById('cfg-api-key');
  const cfgKeyStatus = document.getElementById('cfg-key-status');
  const cfgFeedback = document.getElementById('cfg-feedback');

  // State
  let currentSessionId = localStorage.getItem('active_unity_session') || 'session_001';
  sessionInput.value = currentSessionId;
  let currentRunId = null;
  let isRunning = false;
  let eventSource = null;
  let pollingInterval = null;
  const toolCards = new Map(); // toolCallId -> DOM Element

  // Initialize
  init();

  function init() {
    setupEventListeners();
    connectSse(currentSessionId);
    pollStatus();
    setInterval(pollStatus, 4000);
    loadSessionHistory(currentSessionId);
    loadProviderConfig();
  }

  function setupEventListeners() {
    // Send message on click
    btnSend.addEventListener('click', handleSendMessage);

    // Cancel run on click
    btnCancel.addEventListener('click', handleCancelRun);

    // Textarea enter key and auto-expand
    userPrompt.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSendMessage();
      }
    });

    userPrompt.addEventListener('input', () => {
      userPrompt.style.height = 'auto';
      userPrompt.style.height = Math.min(userPrompt.scrollHeight, 160) + 'px';
    });

    // Session switching
    sessionInput.addEventListener('change', () => {
      const newSession = sessionInput.value.trim();
      if (newSession && newSession !== currentSessionId) {
        switchSession(newSession);
      }
    });

    btnNewSession.addEventListener('click', () => {
      const newSess = 'session_' + Math.random().toString(36).substring(2, 9);
      sessionInput.value = newSess;
      switchSession(newSess);
    });

    // Quick prompts
    document.querySelectorAll('.prompt-chip').forEach(btn => {
      btn.addEventListener('click', () => {
        const prompt = btn.getAttribute('data-prompt');
        if (prompt && !isRunning) {
          userPrompt.value = prompt;
          userPrompt.dispatchEvent(new Event('input'));
          handleSendMessage();
        }
      });
    });

    // Settings modal triggers
    if (btnOpenSettings) {
      btnOpenSettings.addEventListener('click', openSettingsModal);
    }
    if (btnCloseSettings) {
      btnCloseSettings.addEventListener('click', closeSettingsModal);
    }
    if (btnCancelSettings) {
      btnCancelSettings.addEventListener('click', closeSettingsModal);
    }
    if (btnSaveSettings) {
      btnSaveSettings.addEventListener('click', handleSaveSettings);
    }
    if (cfgProvider) {
      cfgProvider.addEventListener('change', handleProviderChange);
    }
  }

  function switchSession(sessionId) {
    currentSessionId = sessionId;
    localStorage.setItem('active_unity_session', sessionId);
    if (eventSource) {
      eventSource.close();
    }
    connectSse(currentSessionId);
    clearChatUI();
    loadSessionHistory(currentSessionId);
  }

  function clearChatUI() {
    chatMessages.innerHTML = '';
    toolCards.clear();
    setRunning(false);
    if (pipelineBanner) pipelineBanner.classList.add('hidden');
    if (goalBanner) goalBanner.classList.add('hidden');
    if (resultsBar) resultsBar.classList.add('hidden');
  }

  // --- Pipeline Stage Tracking (7 Stages) ---
  const PIPELINE_STAGES = ['PLANNING', 'BUILDING', 'COMPILING', 'DIAGNOSING', 'REPAIRING', 'TESTING', 'VALIDATING'];

  function updatePipeline(stage) {
    if (!pipelineBanner) return;
    pipelineBanner.classList.remove('hidden');

    const steps = pipelineBanner.querySelectorAll('.pipeline-step');
    const stageIndex = PIPELINE_STAGES.indexOf(stage);

    if (stage === 'COMPLETED') {
      steps.forEach(s => {
        s.className = 'pipeline-step completed';
      });
      return;
    }

    if (stage === 'FAILED') {
      steps.forEach(s => {
        if (s.classList.contains('active')) {
          s.className = 'pipeline-step failed';
        }
      });
      return;
    }

    if (stageIndex === -1) return;

    steps.forEach((s, idx) => {
      if (idx < stageIndex) {
        s.className = 'pipeline-step completed';
      } else if (idx === stageIndex) {
        s.className = 'pipeline-step active';
      } else {
        s.className = 'pipeline-step';
      }
    });
  }

  function updateGoalBanner(goalText, agentState, planStep) {
    if (!goalBanner) return;
    goalBanner.classList.remove('hidden');

    if (goalText && goalDescription) {
      goalDescription.textContent = goalText;
    }
    if (agentState && agentStateBadge) {
      agentStateBadge.textContent = agentState;
    }
    if (planStep && currentPlanStep) {
      currentPlanStep.textContent = planStep;
    }
  }

  function updateResults(compilationStatus, runtimeStatus, validationStatus) {
    if (!resultsBar) return;
    resultsBar.classList.remove('hidden');

    if (compilationStatus && valCompilation) {
      valCompilation.textContent = compilationStatus;
      valCompilation.className = 'result-value ' + compilationStatus;
    }
    if (runtimeStatus && valRuntime) {
      valRuntime.textContent = runtimeStatus;
      valRuntime.className = 'result-value ' + runtimeStatus;
    }
    if (validationStatus && valValidation) {
      valValidation.textContent = validationStatus;
      valValidation.className = 'result-value ' + validationStatus;
    }
  }

  // --- Settings Modal Logic ---

  async function openSettingsModal() {
    if (!settingsModal) return;
    settingsModal.classList.remove('hidden');
    cfgFeedback.classList.add('hidden');
    cfgApiKey.value = '';
    await loadProviderConfig();
  }

  function closeSettingsModal() {
    if (!settingsModal) return;
    settingsModal.classList.add('hidden');
  }

  function handleProviderChange() {
    const selected = cfgProvider.value;
    if (selected === 'openai') {
      cfgBaseUrl.value = 'https://api.openai.com/v1';
      if (!cfgModel.value || cfgModel.value.includes('llama')) {
        cfgModel.value = 'gpt-4o';
      }
    } else if (selected === 'openai-compatible') {
      if (cfgBaseUrl.value === 'https://api.openai.com/v1') {
        cfgBaseUrl.value = 'https://integrate.api.nvidia.com/v1';
      }
      if (!cfgModel.value || cfgModel.value.startsWith('gpt-')) {
        cfgModel.value = 'meta/llama-3.1-70b-instruct';
      }
    }
  }

  async function loadProviderConfig() {
    try {
      const res = await fetch('/api/config/provider');
      if (res.ok) {
        const data = await res.json();
        if (cfgProvider && data.provider) cfgProvider.value = data.provider;
        if (cfgBaseUrl && data.baseUrl) cfgBaseUrl.value = data.baseUrl;
        if (cfgModel && data.model) cfgModel.value = data.model;
        if (cfgKeyStatus) {
          cfgKeyStatus.textContent = data.hasApiKey
            ? `Key configured: Yes (${data.maskedApiKey || '***'})`
            : 'Key configured: No key set';
        }

        // Update badge
        if (badgeProviderText) {
          const modelName = data.model ? data.model.split('/').pop() : 'LLM';
          badgeProviderText.textContent = `${data.provider || 'AI'}: ${modelName}`;
        }
      }
    } catch (e) {
      console.warn('Failed to load provider config:', e);
    }
  }

  async function handleSaveSettings() {
    btnSaveSettings.disabled = true;
    btnSaveSettings.textContent = 'Saving...';
    cfgFeedback.className = 'settings-feedback hidden';

    const payload = {
      provider: cfgProvider.value,
      baseUrl: cfgBaseUrl.value.trim(),
      model: cfgModel.value.trim()
    };

    const newKey = cfgApiKey.value.trim();
    if (newKey) {
      payload.apiKey = newKey;
    }

    try {
      const res = await fetch('/api/config/provider', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      const data = await res.json();
      if (res.ok) {
        cfgFeedback.textContent = '✓ Configuration updated successfully!';
        cfgFeedback.className = 'settings-feedback success';
        cfgApiKey.value = '';
        if (cfgKeyStatus) {
          cfgKeyStatus.textContent = data.hasApiKey
            ? `Key configured: Yes (${data.maskedApiKey || '***'})`
            : 'Key configured: No key set';
        }
        await pollStatus();
        setTimeout(closeSettingsModal, 1200);
      } else {
        cfgFeedback.textContent = '✗ ' + (data.error || 'Failed to update configuration.');
        cfgFeedback.className = 'settings-feedback error';
      }
    } catch (e) {
      cfgFeedback.textContent = '✗ Network error: ' + e.message;
      cfgFeedback.className = 'settings-feedback error';
    } finally {
      btnSaveSettings.disabled = false;
      btnSaveSettings.textContent = 'Save & Apply';
    }
  }

  // --- Backend Communication ---

  async function handleSendMessage() {
    const text = userPrompt.value.trim();
    if (!text || isRunning) return;

    userPrompt.value = '';
    userPrompt.style.height = 'auto';

    if (welcomeCard && welcomeCard.parentNode) {
      welcomeCard.remove();
    }

    // Append user message
    appendUserMessage(text);
    setRunning(true, 'Initiating agent run...');
    updateGoalBanner(text, 'PLANNING', 'Analyzing user prompt...');
    updatePipeline('PLANNING');
    updateResults('CLEAN', 'CLEAN', 'PENDING');

    try {
      const res = await fetch('/api/agent/run', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId: currentSessionId,
          message: text,
          async: true
        })
      });

      if (res.status === 409) {
        const err = await res.json();
        appendSystemAlert('Conflict: ' + (err.message || 'Another run is already active for this session.'));
        setRunning(false);
        return;
      }

      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        appendSystemAlert('Error starting run: ' + (err.message || res.statusText));
        setRunning(false);
        return;
      }

      const data = await res.json();
      currentRunId = data.agentRunId;
      activityRunBadge.textContent = currentRunId || '';
      setRunning(true, 'Thinking...');

      // Start fallback poller in case SSE has dropped
      startFallbackPolling(currentRunId);

    } catch (e) {
      appendSystemAlert('Failed to connect to backend: ' + e.message);
      setRunning(false);
    }
  }

  async function handleCancelRun() {
    if (!currentRunId) return;
    btnCancel.disabled = true;
    btnCancel.textContent = 'Cancelling...';

    try {
      await fetch(`/api/agent/run/${encodeURIComponent(currentRunId)}/cancel`, { method: 'POST' });
      setActivityText('Run cancellation requested...');
      updateGoalBanner(null, 'CANCELLING', 'Cancelling task...');
    } catch (e) {
      console.error('Cancel request error:', e);
    }
  }

  // --- Server-Sent Events (SSE) ---

  function connectSse(sessionId) {
    if (eventSource) {
      eventSource.close();
    }

    eventSource = new EventSource(`/api/agent/events/${encodeURIComponent(sessionId)}`);

    eventSource.onopen = () => {
      console.log('SSE connected for session:', sessionId);
    };

    eventSource.addEventListener('RUN_STARTED', (e) => {
      const data = JSON.parse(e.data);
      currentRunId = data.agentRunId;
      setRunning(true, 'Thinking...');
      updateGoalBanner(null, 'RUNNING', 'Agent started');
    });

    eventSource.addEventListener('AGENT_ACTIVITY', (e) => {
      const data = JSON.parse(e.data);
      setActivityText(data.activity || 'Thinking...');
    });

    eventSource.addEventListener('TOOL_STARTED', (e) => {
      const data = JSON.parse(e.data);
      setActivityText('Executing ' + data.tool + '...');
      if (data.tool && (data.tool.startsWith('create_') || data.tool.startsWith('set_') || data.tool.startsWith('add_') || data.tool.startsWith('generate_'))) {
        updatePipeline('BUILDING');
        updateGoalBanner(null, 'BUILDING', 'Tool: ' + data.tool);
      }
      renderOrUpdateToolCard(data.toolCallId, data.tool, 'RUNNING', data.arguments, null);
    });

    eventSource.addEventListener('TOOL_COMPLETED', (e) => {
      const data = JSON.parse(e.data);
      renderOrUpdateToolCard(data.toolCallId, data.tool, 'SUCCESS', null, data.resultData);
    });

    eventSource.addEventListener('TOOL_FAILED', (e) => {
      const data = JSON.parse(e.data);
      renderOrUpdateToolCard(data.toolCallId, data.tool, 'FAILED', null, data.errorMessage);
    });

    // Phase 6 Lifecycle Events
    eventSource.addEventListener('GOAL_CREATED', (e) => {
      const data = JSON.parse(e.data);
      updatePipeline('PLANNING');
      updateGoalBanner(data.activity || 'Autonomous build', 'PLANNING', 'Synthesizing build plan...');
      setActivityText('Goal: ' + (data.activity || 'Planning build...'));
    });

    eventSource.addEventListener('PLAN_CREATED', (e) => {
      const data = JSON.parse(e.data);
      updatePipeline('PLANNING');
      updateGoalBanner(null, 'PLANNING', data.activity || 'Plan created');
      setActivityText('Planning build...');
    });

    eventSource.addEventListener('PLAN_STEP_STARTED', (e) => {
      const data = JSON.parse(e.data);
      updatePipeline('BUILDING');
      updateGoalBanner(null, 'BUILDING', data.activity || 'Executing plan step...');
      setActivityText(data.activity || 'Executing plan step...');
    });

    eventSource.addEventListener('PLAN_STEP_COMPLETED', (e) => {
      const data = JSON.parse(e.data);
      updateGoalBanner(null, 'BUILDING', (data.activity || 'Step completed'));
      setActivityText(data.activity || 'Plan step completed');
    });

    eventSource.addEventListener('COMPILATION_STARTED', () => {
      updatePipeline('COMPILING');
      updateGoalBanner(null, 'COMPILING', 'Triggering Unity compilation...');
      updateResults('CHECKING', null, null);
      setActivityText('Compiling project...');
    });

    eventSource.addEventListener('COMPILATION_COMPLETED', (e) => {
      const data = JSON.parse(e.data);
      const errors = data.errorCount || (data.activity && data.activity.toLowerCase().includes('error'));
      updateResults(errors ? 'ERRORS' : 'CLEAN', null, null);
      setActivityText(data.activity || 'Compilation finished.');
    });

    eventSource.addEventListener('DIAGNOSIS_STARTED', (e) => {
      const data = JSON.parse(e.data);
      updatePipeline('DIAGNOSING');
      updateGoalBanner(null, 'DIAGNOSING', 'Analyzing compiler errors...');
      setActivityText(data.activity || 'Diagnosing compiler errors...');
    });

    eventSource.addEventListener('REPAIR_STARTED', (e) => {
      const data = JSON.parse(e.data);
      updatePipeline('REPAIRING');
      updateGoalBanner(null, 'REPAIRING', 'Generating script fix...');
      setActivityText(data.activity || 'Repairing script...');
    });

    eventSource.addEventListener('RECOVERY_LIMIT_REACHED', (e) => {
      const data = JSON.parse(e.data);
      updatePipeline('FAILED');
      updateGoalBanner(null, 'FAILED', 'Compile recovery failed');
      setActivityText(data.activity || 'Recovery limit reached.');
      appendSystemAlert('Compile recovery limit reached: ' + (data.activity || ''));
    });

    eventSource.addEventListener('RUNTIME_TEST_STARTED', () => {
      updatePipeline('TESTING');
      updateGoalBanner(null, 'TESTING', 'Running Unity PlayMode test...');
      updateResults(null, 'CHECKING', null);
      setActivityText('Starting runtime test...');
    });

    eventSource.addEventListener('RUNTIME_TEST_COMPLETED', (e) => {
      const data = JSON.parse(e.data);
      const errors = data.errorCount || (data.activity && data.activity.toLowerCase().includes('exception'));
      updateResults(null, errors ? 'ERRORS' : 'CLEAN', null);
      setActivityText(data.activity || 'Runtime test finished.');
    });

    eventSource.addEventListener('VALIDATION_STARTED', () => {
      updatePipeline('VALIDATING');
      updateGoalBanner(null, 'VALIDATING', 'Validating goal criteria...');
      updateResults(null, null, 'CHECKING');
      setActivityText('Validating goal requirements...');
    });

    eventSource.addEventListener('VALIDATION_COMPLETED', (e) => {
      const data = JSON.parse(e.data);
      const passed = !data.activity || !data.activity.toLowerCase().includes('fail');
      updateResults(null, null, passed ? 'PASSED' : 'FAILED');
      setActivityText(data.activity || 'Goal verified.');
    });

    eventSource.addEventListener('GOAL_COMPLETED', () => {
      updatePipeline('COMPLETED');
      updateGoalBanner(null, 'COMPLETED', 'All objectives achieved!');
      setActivityText('✓ Goal completed!');
    });

    eventSource.addEventListener('GOAL_FAILED', (e) => {
      const data = JSON.parse(e.data);
      updatePipeline('FAILED');
      updateGoalBanner(null, 'FAILED', data.errorMessage || 'Goal failed');
      setActivityText('✗ Goal failed: ' + (data.errorMessage || ''));
    });

    eventSource.addEventListener('RUN_COMPLETED', (e) => {
      const data = JSON.parse(e.data);
      stopFallbackPolling();
      setRunning(false);
      updatePipeline('COMPLETED');
      updateGoalBanner(null, 'IDLE', 'Ready');
      if (data.resultData && typeof data.resultData === 'string') {
        appendAssistantMessage(data.resultData);
      }
    });

    eventSource.addEventListener('RUN_FAILED', (e) => {
      const data = JSON.parse(e.data);
      stopFallbackPolling();
      setRunning(false);
      updatePipeline('FAILED');
      updateGoalBanner(null, 'FAILED', data.errorMessage || 'Error');
      appendSystemAlert('Agent run failed: ' + (data.errorMessage || 'Unknown error'));
    });

    eventSource.addEventListener('RUN_CANCELLED', () => {
      stopFallbackPolling();
      setRunning(false);
      updatePipeline('FAILED');
      updateGoalBanner(null, 'CANCELLED', 'User cancelled run');
      appendSystemAlert('Agent run was cancelled.');
    });

    eventSource.onerror = (err) => {
      console.warn('SSE connection issue, relying on HTTP polling:', err);
    };
  }

  // --- Fallback Polling ---

  function startFallbackPolling(runId) {
    stopFallbackPolling();
    pollingInterval = setInterval(async () => {
      if (!isRunning || !runId) {
        stopFallbackPolling();
        return;
      }

      try {
        const res = await fetch(`/api/agent/run/${encodeURIComponent(runId)}`);
        if (res.ok) {
          const state = await res.json();
          if (state.currentActivity) {
            setActivityText(state.currentActivity);
          }
          if (state.status === 'COMPLETED') {
            stopFallbackPolling();
            setRunning(false);
            updatePipeline('COMPLETED');
            if (state.result && state.result.response) {
              appendAssistantMessage(state.result.response);
            }
          } else if (state.status === 'FAILED') {
            stopFallbackPolling();
            setRunning(false);
            updatePipeline('FAILED');
            appendSystemAlert('Agent run failed: ' + (state.errorMessage || 'Unknown error'));
          } else if (state.status === 'CANCELLED') {
            stopFallbackPolling();
            setRunning(false);
            updatePipeline('FAILED');
            appendSystemAlert('Agent run was cancelled.');
          }
        }
      } catch (e) {
        console.debug('Polling check error:', e);
      }
    }, 1500);
  }

  function stopFallbackPolling() {
    if (pollingInterval) {
      clearInterval(pollingInterval);
      pollingInterval = null;
    }
  }

  // --- Load Session History ---

  async function loadSessionHistory(sessionId) {
    try {
      const res = await fetch(`/api/agent/session/${encodeURIComponent(sessionId)}`);
      if (res.ok) {
        const data = await res.json();
        if (data.messages && data.messages.length > 0) {
          if (welcomeCard && welcomeCard.parentNode) {
            welcomeCard.remove();
          }
          data.messages.forEach(msg => {
            if (msg.role === 'USER') {
              appendUserMessage(msg.content);
            } else if (msg.role === 'ASSISTANT' && msg.content) {
              appendAssistantMessage(msg.content);
            }
          });
        }
        if (data.active && data.activeRunId) {
          currentRunId = data.activeRunId;
          setRunning(true, 'Run in progress...');
          startFallbackPolling(currentRunId);
        }
      }
    } catch (e) {
      console.warn('Failed to load session history:', e);
    }
  }

  // --- Health & Provider Polling ---

  async function pollStatus() {
    try {
      const res = await fetch('/api/status');
      if (res.ok) {
        const data = await res.json();
        updateBadge(badgeBackend, data.backend && data.backend.available, 'Backend');
        updateBadge(badgeUnity, data.unity && data.unity.connected, 'Unity');

        const llmOnline = data.llm && data.llm.configured && data.llm.available;
        const llmLabel = data.llm && data.llm.provider ? data.llm.provider.toUpperCase() : 'AI';
        const modelLabel = data.llm && data.llm.model ? data.llm.model.split('/').pop() : '';
        const providerText = modelLabel ? `${llmLabel}: ${modelLabel}` : llmLabel;
        updateBadge(badgeProvider, llmOnline, providerText);
      } else {
        updateBadge(badgeBackend, false, 'Backend');
        updateBadge(badgeUnity, false, 'Unity');
        updateBadge(badgeProvider, false, 'AI Provider');
      }
    } catch {
      updateBadge(badgeBackend, false, 'Backend');
      updateBadge(badgeUnity, false, 'Unity');
      updateBadge(badgeProvider, false, 'AI Provider');
    }
  }

  function updateBadge(el, isOnline, label) {
    if (!el) return;
    el.className = 'status-pill ' + (isOnline ? 'status-online' : 'status-offline');
    const lbl = el.querySelector('.status-label');
    if (lbl) lbl.textContent = label + (isOnline ? ' Connected' : ' Offline');
  }

  // --- UI State & Rendering ---

  function setRunning(running, activity = '') {
    isRunning = running;
    if (running) {
      activityBanner.classList.remove('hidden');
      activityText.textContent = activity || 'Thinking...';
      activityRunBadge.textContent = currentRunId || '';
      btnCancel.classList.remove('hidden');
      btnCancel.disabled = false;
      btnCancel.textContent = 'Cancel Run';
      btnSend.disabled = true;
    } else {
      activityBanner.classList.add('hidden');
      activityRunBadge.textContent = '';
      btnCancel.classList.add('hidden');
      btnSend.disabled = false;
      currentRunId = null;
    }
  }

  function setActivityText(text) {
    if (activityText) {
      activityText.textContent = text;
    }
  }

  function appendUserMessage(text) {
    const row = document.createElement('div');
    row.className = 'message-row user';
    row.innerHTML = `
      <div class="message-sender">User</div>
      <div class="message-bubble">${escapeHtml(text)}</div>
    `;
    chatMessages.appendChild(row);
    scrollBottomSmart();
  }

  function appendAssistantMessage(markdown) {
    const row = document.createElement('div');
    row.className = 'message-row assistant';
    row.innerHTML = `
      <div class="message-sender">Assistant</div>
      <div class="message-bubble">${renderSafeMarkdown(markdown)}</div>
    `;
    chatMessages.appendChild(row);
    scrollBottomSmart();
  }

  function appendSystemAlert(text) {
    const row = document.createElement('div');
    row.className = 'message-row system';
    row.style.alignSelf = 'center';
    row.innerHTML = `
      <div class="message-bubble" style="background-color: var(--bg-card); border: 1px solid var(--border-subtle); font-size: 12px; color: var(--text-muted);">
        ⚠️ ${escapeHtml(text)}
      </div>
    `;
    chatMessages.appendChild(row);
    scrollBottomSmart();
  }

  function renderOrUpdateToolCard(toolCallId, toolName, status, args, result) {
    let card = toolCards.get(toolCallId);
    if (!card) {
      card = document.createElement('div');
      card.className = 'tool-card';
      card.innerHTML = `
        <div class="tool-card-header">
          <div class="tool-title">
            <span>🔧</span>
            <span>${escapeHtml(toolName || 'tool')}</span>
          </div>
          <span class="tool-status-badge ${status}">${status}</span>
        </div>
        <div class="tool-card-body">
          <div class="tool-section args-section">
            <div class="tool-section-label">Arguments</div>
            <div class="tool-data-block args-data"></div>
          </div>
          <div class="tool-section result-section" style="display: none;">
            <div class="tool-section-label">Result</div>
            <div class="tool-data-block result-data"></div>
          </div>
        </div>
      `;

      // Toggle collapse on header click
      const header = card.querySelector('.tool-card-header');
      const body = card.querySelector('.tool-card-body');
      header.addEventListener('click', () => {
        body.classList.toggle('collapsed');
      });

      chatMessages.appendChild(card);
      toolCards.set(toolCallId, card);
    }

    // Update status badge
    const badge = card.querySelector('.tool-status-badge');
    badge.className = `tool-status-badge ${status}`;
    badge.textContent = status;

    // Update arguments if provided
    if (args) {
      const argsEl = card.querySelector('.args-data');
      argsEl.textContent = typeof args === 'string' ? args : JSON.stringify(args, null, 2);
    }

    // Update result if provided
    if (result !== undefined && result !== null) {
      const resSection = card.querySelector('.result-section');
      const resEl = card.querySelector('.result-data');
      resSection.style.display = 'block';
      resEl.textContent = typeof result === 'string' ? result : JSON.stringify(result, null, 2);
    }

    scrollBottomSmart();
  }

  function scrollBottomSmart() {
    // Scroll only if user is already within 140px of bottom
    const distanceToBottom = chatMessages.scrollHeight - chatMessages.scrollTop - chatMessages.clientHeight;
    if (distanceToBottom < 140) {
      chatMessages.scrollTop = chatMessages.scrollHeight;
    }
  }

  // --- Safe Markdown Renderer ---

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function renderSafeMarkdown(md) {
    if (!md) return '';

    // First sanitize raw HTML entities
    let text = escapeHtml(md);

    // Code blocks ```code```
    text = text.replace(/```([a-zA-Z0-9_]*)\n([\s\S]*?)```/g, (match, lang, code) => {
      return `<pre><code>${code.trim()}</code></pre>`;
    });

    // Inline code `code`
    text = text.replace(/`([^`]+)`/g, '<code>$1</code>');

    // Tables
    text = text.replace(/((?:\|[^\n]+\|\r?\n)+)/g, (match) => {
      const rows = match.trim().split('\n').map(r => r.trim()).filter(Boolean);
      if (rows.length < 2) return match;
      let html = '<table>';
      rows.forEach((row, idx) => {
        if (row.includes('---')) return; // separator row
        const cells = row.split('|').map(c => c.trim()).slice(1, -1);
        const tag = idx === 0 ? 'th' : 'td';
        html += '<tr>' + cells.map(c => `<${tag}>${c}</${tag}>`).join('') + '</tr>';
      });
      html += '</table>';
      return html;
    });

    // Bold **text**
    text = text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

    // Italic *text*
    text = text.replace(/\*([^*]+)\*/g, '<em>$1</em>');

    // Bullet lists
    text = text.replace(/(?:^|\n)- ([^\n]+)/g, '<br>• $1');

    // Paragraphs / newlines
    text = text.replace(/\n\n/g, '<br><br>');
    text = text.replace(/\n/g, '<br>');

    return text;
  }

})();
