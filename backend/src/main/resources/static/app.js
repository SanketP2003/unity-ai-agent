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
  const badgeBreaker = document.getElementById('badge-breaker');
  const badgeBreakerText = document.getElementById('badge-breaker-text');

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

  // DOM Elements - Wizard Modal
  const btnOpenWizard = document.getElementById('btn-open-wizard');
  const btnCloseWizard = document.getElementById('btn-close-wizard');
  const btnWizClose = document.getElementById('btn-wiz-close');
  const btnWizTestProv = document.getElementById('btn-wiz-test-prov');
  const btnWizRunAudit = document.getElementById('btn-wiz-run-audit');
  const btnWizRefresh = document.getElementById('btn-wiz-refresh');
  const wizardModal = document.getElementById('wizard-modal');
  const wizSysBadge = document.getElementById('wiz-sys-badge');
  const wizJava = document.getElementById('wiz-java');
  const wizMemory = document.getElementById('wiz-memory');
  const wizDisk = document.getElementById('wiz-disk');
  const wizWrite = document.getElementById('wiz-write');
  const wizSqlite = document.getElementById('wiz-sqlite');
  const wizProvBadge = document.getElementById('wiz-prov-badge');
  const wizProviderSelect = document.getElementById('wiz-provider-select');
  const wizApiKey = document.getElementById('wiz-api-key');
  const wizProvFeedback = document.getElementById('wiz-prov-feedback');
  const wizUnityBadge = document.getElementById('wiz-unity-badge');
  const wizUnityState = document.getElementById('wiz-unity-state');
  const wizUnityProjects = document.getElementById('wiz-unity-projects');
  const wizSecBadge = document.getElementById('wiz-sec-badge');
  const wizSecFeedback = document.getElementById('wiz-sec-feedback');
  const wizReadinessBanner = document.getElementById('wiz-readiness-banner');
  const wizReadinessTitle = document.getElementById('wiz-readiness-title');
  const wizReadinessDesc = document.getElementById('wiz-readiness-desc');

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
    restoreActiveAutonomyRun();
    initStudio();
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

    // Wizard modal triggers
    if (btnOpenWizard) {
      btnOpenWizard.addEventListener('click', openWizardModal);
    }
    if (btnCloseWizard) {
      btnCloseWizard.addEventListener('click', closeWizardModal);
    }
    if (btnWizClose) {
      btnWizClose.addEventListener('click', closeWizardModal);
    }
    if (btnWizRefresh) {
      btnWizRefresh.addEventListener('click', refreshWizardStatus);
    }
    if (btnWizTestProv) {
      btnWizTestProv.addEventListener('click', handleWizardTestProvider);
    }
    if (btnWizRunAudit) {
      btnWizRunAudit.addEventListener('click', handleWizardRunSecurityAudit);
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

    // Phase 10 Metrics & Circuit Breaker polling
    try {
      const mRes = await fetch('/api/metrics');
      if (mRes.ok) {
        const mData = await mRes.json();
        updateCircuitBreakerBadge(mData.circuitBreakerStatus || 'CLOSED');
        updateDiagnosticsGrid(mData);
      }
    } catch (e) {
      console.debug('Metrics poll error:', e);
    }
  }

  function updateCircuitBreakerBadge(state) {
    if (!badgeBreaker) return;
    const s = (state || 'CLOSED').toUpperCase();
    if (s === 'CLOSED') {
      badgeBreaker.className = 'status-pill status-online';
      if (badgeBreakerText) badgeBreakerText.textContent = 'Breaker: CLOSED';
    } else if (s === 'HALF_OPEN') {
      badgeBreaker.className = 'status-pill status-checking';
      if (badgeBreakerText) badgeBreakerText.textContent = 'Breaker: HALF_OPEN';
    } else {
      badgeBreaker.className = 'status-pill status-offline';
      if (badgeBreakerText) badgeBreakerText.textContent = 'Breaker: OPEN';
    }
  }

  function updateDiagnosticsGrid(m) {
    const elTotal = document.getElementById('diag-total-runs');
    if (elTotal) elTotal.textContent = m.totalRunsInitiated != null ? m.totalRunsInitiated : 0;
    const elActive = document.getElementById('diag-active-runs');
    if (elActive) elActive.textContent = m.activeRunsCount != null ? m.activeRunsCount : 0;
    const elSuccess = document.getElementById('diag-success-rate');
    if (elSuccess) {
      const rate = (m.successRate != null) ? (m.successRate * 100).toFixed(1) + '%' : '100%';
      elSuccess.textContent = rate;
    }
    const elRec = document.getElementById('diag-recoveries');
    if (elRec) elRec.textContent = m.totalRecoveriesTriggered != null ? m.totalRecoveriesTriggered : 0;
    const elDisc = document.getElementById('diag-disconnects');
    if (elDisc) elDisc.textContent = m.totalUnityDisconnects != null ? m.totalUnityDisconnects : 0;
    const elTrips = document.getElementById('diag-breaker-trips');
    if (elTrips) elTrips.textContent = m.circuitBreakerTrips != null ? m.circuitBreakerTrips : 0;
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

  // --- Phase 9/10 Autonomy Engine Dashboard & Recovery Logic ---
  let activeAutonomyRunId = localStorage.getItem('active_autonomy_run') || null;

  const btnToggleAutonomy = document.getElementById('btn-toggle-autonomy');
  const autonomyPanel = document.getElementById('autonomy-panel');
  const btnCloseAutonomy = document.getElementById('btn-close-autonomy');
  const btnAutonomyStep = document.getElementById('btn-autonomy-step');
  const btnAutonomyPause = document.getElementById('btn-autonomy-pause');
  const btnAutonomyResume = document.getElementById('btn-autonomy-resume');
  const btnAutonomyValidate = document.getElementById('btn-autonomy-validate');
  const btnApproveIntervention = document.getElementById('btn-approve-intervention');
  const btnRejectIntervention = document.getElementById('btn-reject-intervention');
  const autonomyStatusBadge = document.getElementById('autonomy-status-badge');
  const interventionBanner = document.getElementById('intervention-banner');
  const interventionMessage = document.getElementById('intervention-message');
  const nodesListContainer = document.getElementById('autonomy-nodes-list');

  // Tab Navigation Elements
  const tabBtnDag = document.getElementById('tab-btn-dag');
  const tabBtnJournal = document.getElementById('tab-btn-journal');
  const tabBtnDiagnostics = document.getElementById('tab-btn-diagnostics');
  const tabContentDag = document.getElementById('tab-content-dag');
  const tabContentJournal = document.getElementById('tab-content-journal');
  const tabContentDiagnostics = document.getElementById('tab-content-diagnostics');

  function switchAutonomyTab(tabName) {
    [tabBtnDag, tabBtnJournal, tabBtnDiagnostics].forEach(btn => btn && btn.classList.remove('active'));
    [tabContentDag, tabContentJournal, tabContentDiagnostics].forEach(cnt => {
      if (cnt) {
        cnt.classList.remove('active');
        cnt.classList.add('hidden');
      }
    });

    if (tabName === 'dag') {
      if (tabBtnDag) tabBtnDag.classList.add('active');
      if (tabContentDag) { tabContentDag.classList.remove('hidden'); tabContentDag.classList.add('active'); }
    } else if (tabName === 'journal') {
      if (tabBtnJournal) tabBtnJournal.classList.add('active');
      if (tabContentJournal) { tabContentJournal.classList.remove('hidden'); tabContentJournal.classList.add('active'); }
      if (activeAutonomyRunId) loadEventJournal(activeAutonomyRunId);
    } else if (tabName === 'diagnostics') {
      if (tabBtnDiagnostics) tabBtnDiagnostics.classList.add('active');
      if (tabContentDiagnostics) { tabContentDiagnostics.classList.remove('hidden'); tabContentDiagnostics.classList.add('active'); }
    }
  }

  if (tabBtnDag) tabBtnDag.addEventListener('click', () => switchAutonomyTab('dag'));
  if (tabBtnJournal) tabBtnJournal.addEventListener('click', () => switchAutonomyTab('journal'));
  if (tabBtnDiagnostics) tabBtnDiagnostics.addEventListener('click', () => switchAutonomyTab('diagnostics'));

  if (btnToggleAutonomy && autonomyPanel) {
    btnToggleAutonomy.addEventListener('click', () => {
      autonomyPanel.classList.toggle('hidden');
      if (!autonomyPanel.classList.contains('hidden') && activeAutonomyRunId) {
        refreshAutonomyState(activeAutonomyRunId);
      }
    });
  }

  if (btnCloseAutonomy && autonomyPanel) {
    btnCloseAutonomy.addEventListener('click', () => {
      autonomyPanel.classList.add('hidden');
    });
  }

  async function restoreActiveAutonomyRun() {
    const saved = localStorage.getItem('active_autonomy_run');
    if (saved) {
      activeAutonomyRunId = saved;
      await refreshAutonomyState(saved);
    }
  }

  async function refreshAutonomyState(runId) {
    if (!runId) return;
    try {
      // 1. Try fetching in-memory active state
      const res = await fetch(`/api/autonomy/${encodeURIComponent(runId)}/state`);
      if (res.ok) {
        const state = await res.json();
        renderAutonomyDashboard(state);
      } else {
        // 2. Fallback to persisted SQLite record if backend was restarted
        const recRes = await fetch(`/api/autonomy/runs/${encodeURIComponent(runId)}/record`);
        if (recRes.ok) {
          const record = await recRes.json();
          renderAutonomyFromRecord(record);
        }
      }
      // Always load durable event journal history
      loadEventJournal(runId);
    } catch (err) {
      console.warn('Failed to refresh autonomy state:', err);
    }
  }

  function renderAutonomyDashboard(state) {
    if (!state) return;
    activeAutonomyRunId = state.runId;
    localStorage.setItem('active_autonomy_run', state.runId);

    if (autonomyStatusBadge) {
      autonomyStatusBadge.textContent = state.status || 'IDLE';
      autonomyStatusBadge.className = `badge badge-${(state.status || 'idle').toLowerCase()}`;
    }

    // Update buttons
    const isRunning = state.status === 'RUNNING';
    const isPaused = state.status === 'PAUSED';
    if (btnAutonomyStep) btnAutonomyStep.disabled = !isRunning;
    if (btnAutonomyPause) btnAutonomyPause.disabled = !isRunning;
    if (btnAutonomyResume) btnAutonomyResume.disabled = !isPaused;

    // Metrics
    const completed = state.executionState ? (state.executionState.completedNodes ? Object.keys(state.executionState.completedNodes).length : 0) : 0;
    const total = state.plan && state.plan.planNodes ? Object.keys(state.plan.planNodes).length : 0;
    const mNodes = document.getElementById('metric-nodes');
    if (mNodes) mNodes.textContent = `${completed}/${total}`;

    const mToolCalls = document.getElementById('metric-tool-calls');
    if (mToolCalls) mToolCalls.textContent = state.executionState ? state.executionState.totalToolCalls : 0;

    const mRecovery = document.getElementById('metric-recovery');
    if (mRecovery) mRecovery.textContent = state.executionState ? state.executionState.recoveryCycles : 0;

    const mReplans = document.getElementById('metric-replans');
    if (mReplans) mReplans.textContent = state.executionState ? state.executionState.replans : 0;

    const mCheckpoint = document.getElementById('metric-checkpoint');
    if (mCheckpoint) mCheckpoint.textContent = state.lastCheckpointId ? state.lastCheckpointId.substring(0, 16) + '...' : 'None';

    // Intervention
    if (state.currentIntervention && !state.currentIntervention.resolved) {
      if (interventionBanner) interventionBanner.classList.remove('hidden');
      if (interventionMessage) interventionMessage.textContent = state.currentIntervention.message;
    } else {
      if (interventionBanner) interventionBanner.classList.add('hidden');
    }

    // Render DAG nodes
    if (nodesListContainer && state.plan && state.plan.planNodes) {
      nodesListContainer.innerHTML = '';
      const nodes = Object.values(state.plan.planNodes);
      if (nodes.length === 0) {
        nodesListContainer.innerHTML = '<div class="empty-dag-note">No nodes in plan.</div>';
        return;
      }

      nodes.forEach(node => {
        const item = document.createElement('div');
        item.className = `dag-node-item node-${node.status}`;
        item.innerHTML = `
          <div class="node-title-group">
            <span class="node-id">${escapeHtml(node.nodeId)}</span>
            <span class="node-desc">${escapeHtml(node.description)}</span>
          </div>
          <span class="node-badge badge-${node.status.toLowerCase()}">${escapeHtml(node.status)}</span>
        `;
        nodesListContainer.appendChild(item);
      });
    }
  }

  function renderAutonomyFromRecord(record) {
    if (!record) return;
    activeAutonomyRunId = record.runId;

    if (autonomyStatusBadge) {
      autonomyStatusBadge.textContent = record.status || 'IDLE';
      autonomyStatusBadge.className = `badge badge-${(record.status || 'idle').toLowerCase()}`;
    }

    const isRunning = record.status === 'RUNNING';
    const isPaused = record.status === 'PAUSED';
    if (btnAutonomyStep) btnAutonomyStep.disabled = !isRunning;
    if (btnAutonomyPause) btnAutonomyPause.disabled = !isRunning;
    if (btnAutonomyResume) btnAutonomyResume.disabled = !isPaused;

    const mNodes = document.getElementById('metric-nodes');
    if (mNodes) mNodes.textContent = `${record.completedNodes ? record.completedNodes.length : 0}/-`;

    const mReplans = document.getElementById('metric-replans');
    if (mReplans) mReplans.textContent = record.currentPlanRevision != null ? record.currentPlanRevision : 0;

    const mCheckpoint = document.getElementById('metric-checkpoint');
    if (mCheckpoint) mCheckpoint.textContent = record.checkpointRef ? record.checkpointRef.substring(0, 16) + '...' : 'None';
  }

  async function loadEventJournal(runId) {
    const listEl = document.getElementById('autonomy-journal-list');
    if (!listEl || !runId) return;
    try {
      const res = await fetch(`/api/autonomy/runs/${encodeURIComponent(runId)}/events`);
      if (res.ok) {
        const events = await res.json();
        renderEventJournal(events);
      }
    } catch (e) {
      console.warn('Failed to load event journal:', e);
    }
  }

  function renderEventJournal(events) {
    const listEl = document.getElementById('autonomy-journal-list');
    if (!listEl) return;
    if (!events || events.length === 0) {
      listEl.innerHTML = '<div class="empty-dag-note">No events recorded yet for this run.</div>';
      return;
    }

    listEl.innerHTML = '';
    events.forEach(evt => {
      const item = document.createElement('div');
      item.className = `journal-item evt-${escapeHtml(evt.eventType)}`;
      const timeStr = evt.createdAt ? new Date(evt.createdAt).toLocaleTimeString() : '';
      const payloadStr = evt.payload ? (typeof evt.payload === 'string' ? evt.payload : JSON.stringify(evt.payload)) : '';

      item.innerHTML = `
        <span class="journal-seq">#${evt.sequenceNumber != null ? evt.sequenceNumber : ''}</span>
        <span class="journal-time">${escapeHtml(timeStr)}</span>
        <span class="journal-type">${escapeHtml(evt.eventType || '')}</span>
        <span class="journal-payload" title="${escapeHtml(payloadStr)}">${escapeHtml(payloadStr.substring(0, 100))}${payloadStr.length > 100 ? '...' : ''}</span>
      `;
      listEl.appendChild(item);
    });
  }

  if (btnAutonomyStep) {
    btnAutonomyStep.addEventListener('click', async () => {
      if (!activeAutonomyRunId) return;
      btnAutonomyStep.disabled = true;
      try {
        const res = await fetch(`/api/autonomy/${activeAutonomyRunId}/step`, { method: 'POST' });
        if (res.ok) {
          await refreshAutonomyState(activeAutonomyRunId);
        }
      } catch (err) {
        console.error('Step execution error:', err);
      } finally {
        btnAutonomyStep.disabled = false;
      }
    });
  }

  if (btnAutonomyPause) {
    btnAutonomyPause.addEventListener('click', async () => {
      if (!activeAutonomyRunId) return;
      await fetch(`/api/autonomy/${activeAutonomyRunId}/pause`, { method: 'POST' });
      await refreshAutonomyState(activeAutonomyRunId);
    });
  }

  if (btnAutonomyResume) {
    btnAutonomyResume.addEventListener('click', async () => {
      if (!activeAutonomyRunId) return;
      await fetch(`/api/autonomy/${activeAutonomyRunId}/resume`, { method: 'POST' });
      await refreshAutonomyState(activeAutonomyRunId);
    });
  }

  if (btnAutonomyValidate) {
    btnAutonomyValidate.addEventListener('click', async () => {
      if (!activeAutonomyRunId) {
        alert('No active autonomy run to validate.');
        return;
      }
      try {
        const res = await fetch(`/api/autonomy/${activeAutonomyRunId}/validation`);
        if (res.ok) {
          const val = await res.json();
          alert(`Completion Gate: ${val.gatePassed ? 'PASSED ✅' : 'REJECTED ❌'}\n\nSummary: ${val.summary || 'N/A'}`);
        }
      } catch (e) {
        alert('Failed to fetch validation: ' + e.message);
      }
    });
  }

  if (btnApproveIntervention) {
    btnApproveIntervention.addEventListener('click', async () => {
      if (!activeAutonomyRunId) return;
      await fetch(`/api/autonomy/${activeAutonomyRunId}/intervene`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ approve: true, notes: 'Approved by user via dashboard' })
      });
      await refreshAutonomyState(activeAutonomyRunId);
    });
  }

  if (btnRejectIntervention) {
    btnRejectIntervention.addEventListener('click', async () => {
      if (!activeAutonomyRunId) return;
      await fetch(`/api/autonomy/${activeAutonomyRunId}/intervene`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ approve: false, notes: 'Rejected by user via dashboard' })
      });
      await refreshAutonomyState(activeAutonomyRunId);
    });
  }

  // =========================================================================
  // Phase 11 — Professional Autonomous Game Studio Workspace Logic
  // =========================================================================

  let activeStudioProjectId = localStorage.getItem('studio_active_project') || 'default';
  let activeStudioView = 'view-workspace';
  let activeAssetCategory = '';
  let activeSelectedGameObject = null;
  let activeSelectedScript = null;

  function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function initStudio() {
    setupStudioNavigation();
    setupStudioProjectSelector();
    setupStudioRefreshButtons();
    setupStudioBuilds();
    setupStudioAssetFilter();
    setupStudioDiagnosticsFilters();
    setupStudioReleases();
    setupStudioBackups();
    setupStudioSettings();

    // Initial load
    loadStudioProjects();
    checkPendingChangesCount(activeStudioProjectId);
    setInterval(() => checkPendingChangesCount(activeStudioProjectId), 10000);
  }

  // 1. Sidebar Navigation Switcher
  function setupStudioNavigation() {
    const navButtons = document.querySelectorAll('.studio-sidebar .nav-item');
    navButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        const targetViewId = btn.getAttribute('data-view');
        if (!targetViewId) return;
        switchStudioView(targetViewId, btn);
      });
    });
  }

  function switchStudioView(viewId, clickedBtn) {
    activeStudioView = viewId;

    // Update nav active state
    document.querySelectorAll('.studio-sidebar .nav-item').forEach(b => b.classList.remove('active'));
    if (clickedBtn) {
      clickedBtn.classList.add('active');
    } else {
      const match = document.querySelector(`.studio-sidebar .nav-item[data-view="${viewId}"]`);
      if (match) match.classList.add('active');
    }

    // Toggle view containers
    document.querySelectorAll('.studio-view').forEach(view => {
      if (view.id === viewId) {
        view.classList.remove('hidden');
        view.classList.add('active');
      } else {
        view.classList.add('hidden');
        view.classList.remove('active');
      }
    });

    // Dispatch view-specific data loading
    switch (viewId) {
      case 'view-projects':
        loadStudioProjects();
        loadProjectActivity(activeStudioProjectId);
        break;
      case 'view-scene':
        loadLiveScene(activeStudioProjectId);
        break;
      case 'view-scripts':
        loadProjectScripts(activeStudioProjectId);
        break;
      case 'view-assets':
        loadProjectAssets(activeStudioProjectId, activeAssetCategory);
        break;
      case 'view-changes':
        loadPendingChanges(activeStudioProjectId);
        break;
      case 'view-builds':
        loadBuildHistory(activeStudioProjectId);
        break;
      case 'view-diagnostics':
        loadStudioDiagnostics();
        break;
      case 'view-releases':
        loadReleases(activeStudioProjectId);
        break;
      case 'view-backups':
        loadBackups(activeStudioProjectId);
        break;
      case 'view-settings':
        loadSettingsView(activeStudioProjectId);
        break;
      default:
        break;
    }
  }

  // 2. Project Selector Dropdown
  function setupStudioProjectSelector() {
    const select = document.getElementById('studio-project-select');
    if (!select) return;

    select.addEventListener('change', () => {
      activeStudioProjectId = select.value;
      localStorage.setItem('studio_active_project', activeStudioProjectId);
      checkPendingChangesCount(activeStudioProjectId);

      // Refresh whatever view is currently visible
      switchStudioView(activeStudioView);
    });
  }

  // 3. Refresh Buttons Wiring
  function setupStudioRefreshButtons() {
    const btnRefProjects = document.getElementById('btn-refresh-projects');
    if (btnRefProjects) {
      btnRefProjects.addEventListener('click', () => {
        loadStudioProjects();
        loadProjectActivity(activeStudioProjectId);
      });
    }

    const btnRefScene = document.getElementById('btn-refresh-scene');
    if (btnRefScene) {
      btnRefScene.addEventListener('click', () => loadLiveScene(activeStudioProjectId));
    }

    const btnRefScripts = document.getElementById('btn-refresh-scripts');
    if (btnRefScripts) {
      btnRefScripts.addEventListener('click', () => loadProjectScripts(activeStudioProjectId));
    }

    const btnRefAssets = document.getElementById('btn-refresh-assets');
    if (btnRefAssets) {
      btnRefAssets.addEventListener('click', () => loadProjectAssets(activeStudioProjectId, activeAssetCategory));
    }

    const btnRefChanges = document.getElementById('btn-refresh-changes');
    if (btnRefChanges) {
      btnRefChanges.addEventListener('click', () => loadPendingChanges(activeStudioProjectId));
    }

    const btnRefDiag = document.getElementById('btn-refresh-diagnostics');
    if (btnRefDiag) {
      btnRefDiag.addEventListener('click', () => loadStudioDiagnostics());
    }
  }

  // ── View 2: Projects Dashboard ──────────────────────────────────────────
  async function loadStudioProjects() {
    const container = document.getElementById('projects-cards-container');
    const select = document.getElementById('studio-project-select');
    if (!container) return;

    try {
      const res = await fetch('/api/studio/projects');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const projects = await res.json();

      // Update selector
      if (select) {
        select.innerHTML = '';
        projects.forEach(p => {
          const opt = document.createElement('option');
          opt.value = p.projectId;
          opt.textContent = `${p.name || p.projectId} (${p.status || 'ACTIVE'})`;
          if (p.projectId === activeStudioProjectId) opt.selected = true;
          select.appendChild(opt);
        });
        if (!projects.some(p => p.projectId === activeStudioProjectId) && projects.length > 0) {
          activeStudioProjectId = projects[0].projectId;
          select.value = activeStudioProjectId;
        }
      }

      // Render cards
      container.innerHTML = '';
      if (projects.length === 0) {
        container.innerHTML = '<div class="empty-note">No registered projects found.</div>';
        return;
      }

      projects.forEach(p => {
        const card = document.createElement('div');
        const isActive = p.projectId === activeStudioProjectId;
        card.className = `project-card ${isActive ? 'active-project' : ''}`;
        const lastActive = p.lastActiveAt ? new Date(p.lastActiveAt).toLocaleString() : 'Never';
        card.innerHTML = `
          <div class="project-card-header">
            <h4 class="project-title">${escapeHtml(p.name || p.projectId)}</h4>
            <span class="status-pill status-${(p.status || 'ACTIVE').toLowerCase() === 'active' ? 'online' : 'checking'}">
              ${escapeHtml(p.status || 'ACTIVE')}
            </span>
          </div>
          <div class="project-card-body">
            <p class="project-path" title="${escapeHtml(p.projectPath || '')}">${escapeHtml(p.projectPath || 'No local path configured')}</p>
            <div class="project-meta-row">
              <span><strong>Unity:</strong> ${escapeHtml(p.unityVersion || '2022.3 LTS')}</span>
              <span><strong>Last Active:</strong> ${escapeHtml(lastActive)}</span>
            </div>
          </div>
          <div class="project-card-actions">
            <button class="btn btn-small btn-primary btn-select-proj" data-proj="${escapeHtml(p.projectId)}">
              ${isActive ? 'Active Project' : 'Select Project'}
            </button>
          </div>
        `;
        container.appendChild(card);
      });

      container.querySelectorAll('.btn-select-proj').forEach(b => {
        b.addEventListener('click', (e) => {
          const pid = e.currentTarget.getAttribute('data-proj');
          if (pid) {
            activeStudioProjectId = pid;
            localStorage.setItem('studio_active_project', pid);
            if (select) select.value = pid;
            loadStudioProjects();
            loadProjectActivity(pid);
            checkPendingChangesCount(pid);
          }
        });
      });
    } catch (err) {
      console.warn('Failed to load projects:', err);
      container.innerHTML = `<div class="error-note">Failed to load projects: ${escapeHtml(err.message)}</div>`;
    }
  }

  async function loadProjectActivity(projectId) {
    const container = document.getElementById('projects-activity-stream');
    if (!container || !projectId) return;

    try {
      const res = await fetch(`/api/studio/projects/${encodeURIComponent(projectId)}/activity?limit=30`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const activities = await res.json();

      container.innerHTML = '';
      if (!activities || activities.length === 0) {
        container.innerHTML = '<div class="empty-note">No recent activity recorded for this project.</div>';
        return;
      }

      activities.forEach(act => {
        const item = document.createElement('div');
        item.className = 'activity-item';
        const timeStr = act.timestamp ? new Date(act.timestamp).toLocaleTimeString() : '';
        item.innerHTML = `
          <span class="activity-time">${escapeHtml(timeStr)}</span>
          <span class="activity-type-badge">${escapeHtml(act.activityType || 'ACTION')}</span>
          <span class="activity-desc">${escapeHtml(act.summary || '')}</span>
        `;
        container.appendChild(item);
      });
    } catch (e) {
      container.innerHTML = `<div class="error-note">Failed to load activity stream: ${escapeHtml(e.message)}</div>`;
    }
  }

  // ── View 3: Scene Explorer & Inspector ──────────────────────────────────
  async function loadLiveScene(projectId) {
    const treeContainer = document.getElementById('scene-tree-container');
    const inspector = document.getElementById('scene-inspector-container');
    if (!treeContainer || !projectId) return;

    treeContainer.innerHTML = '<div class="spinner-small"></div> Fetching live scene...';
    try {
      const res = await fetch(`/api/studio/projects/${encodeURIComponent(projectId)}/scene`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();

      treeContainer.innerHTML = '';
      const objects = data.objects || (Array.isArray(data) ? data : []);
      if (objects.length === 0) {
        treeContainer.innerHTML = '<div class="empty-note">No GameObjects found in active scene.</div>';
        if (inspector) inspector.innerHTML = '<div class="empty-note">Select a GameObject to inspect.</div>';
        return;
      }

      objects.forEach((obj, idx) => {
        const item = document.createElement('div');
        item.className = `tree-item ${activeSelectedGameObject && activeSelectedGameObject.name === obj.name ? 'selected' : ''}`;
        const hasChildren = obj.children && obj.children.length > 0;
        item.innerHTML = `
          <div class="tree-label">
            <span class="tree-icon">${hasChildren ? '📁' : '🔷'}</span>
            <span class="tree-name">${escapeHtml(obj.name || 'GameObject')}</span>
            <span class="tree-tag-badge">${escapeHtml(obj.tag || 'Untagged')}</span>
          </div>
        `;
        item.addEventListener('click', () => {
          activeSelectedGameObject = obj;
          treeContainer.querySelectorAll('.tree-item').forEach(el => el.classList.remove('selected'));
          item.classList.add('selected');
          renderComponentInspector(obj);
        });
        treeContainer.appendChild(item);
      });

      // Default inspect first object if none selected
      if (!activeSelectedGameObject && objects.length > 0) {
        activeSelectedGameObject = objects[0];
        const first = treeContainer.querySelector('.tree-item');
        if (first) first.classList.add('selected');
        renderComponentInspector(objects[0]);
      }
    } catch (err) {
      treeContainer.innerHTML = `<div class="error-note">Scene fetch failed: ${escapeHtml(err.message)}</div>`;
    }
  }

  function renderComponentInspector(obj) {
    const inspector = document.getElementById('scene-inspector-container');
    if (!inspector || !obj) return;

    const components = obj.components || ['Transform', 'MeshFilter', 'MeshRenderer'];
    const transform = obj.transform || { position: [0, 0, 0], rotation: [0, 0, 0], scale: [1, 1, 1] };

    inspector.innerHTML = `
      <div class="inspector-header">
        <h4 class="obj-name">${escapeHtml(obj.name || 'GameObject')}</h4>
        <div class="obj-flags">
          <label><input type="checkbox" ${obj.active !== false ? 'checked' : ''} disabled> Active</label>
          <span class="obj-tag">Tag: <strong>${escapeHtml(obj.tag || 'Untagged')}</strong></span>
          <span class="obj-layer">Layer: <strong>${escapeHtml(obj.layer || 'Default')}</strong></span>
        </div>
      </div>
      <div class="inspector-card">
        <h5>Transform</h5>
        <div class="vector-field">
          <span class="vec-axis">Pos:</span>
          <span>X: ${(transform.position[0] || 0).toFixed(2)}, Y: ${(transform.position[1] || 0).toFixed(2)}, Z: ${(transform.position[2] || 0).toFixed(2)}</span>
        </div>
        <div class="vector-field">
          <span class="vec-axis">Rot:</span>
          <span>X: ${(transform.rotation[0] || 0).toFixed(2)}, Y: ${(transform.rotation[1] || 0).toFixed(2)}, Z: ${(transform.rotation[2] || 0).toFixed(2)}</span>
        </div>
        <div class="vector-field">
          <span class="vec-axis">Scale:</span>
          <span>X: ${(transform.scale[0] || 1).toFixed(2)}, Y: ${(transform.scale[1] || 1).toFixed(2)}, Z: ${(transform.scale[2] || 1).toFixed(2)}</span>
        </div>
      </div>
      <div class="inspector-card">
        <h5>Attached Components (${components.length})</h5>
        <ul class="components-list">
          ${components.map(c => `<li>⚙️ ${escapeHtml(typeof c === 'string' ? c : (c.type || 'Component'))}</li>`).join('')}
        </ul>
      </div>
    `;
  }

  // ── View 4: Script Explorer & Unified Diff Viewer ─────────────────────────
  async function loadProjectScripts(projectId) {
    const listContainer = document.getElementById('scripts-list-container');
    const diffContainer = document.getElementById('script-diff-view');
    const diffTitle = document.getElementById('script-diff-title');
    if (!listContainer || !projectId) return;

    listContainer.innerHTML = '<div class="spinner-small"></div> Loading scripts...';
    try {
      const res = await fetch(`/api/studio/projects/${encodeURIComponent(projectId)}/scripts`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const scripts = await res.json();

      listContainer.innerHTML = '';
      if (!scripts || scripts.length === 0) {
        listContainer.innerHTML = '<div class="empty-note">No C# scripts registered for this project.</div>';
        if (diffContainer) diffContainer.innerHTML = '<div class="empty-note">Select a script to view AST summary or diffs.</div>';
        return;
      }

      scripts.forEach(s => {
        const item = document.createElement('div');
        item.className = `tree-item ${activeSelectedScript && activeSelectedScript.path === s.path ? 'selected' : ''}`;
        item.innerHTML = `
          <div class="tree-label">
            <span class="tree-icon">📄</span>
            <span class="tree-name">${escapeHtml(s.className || s.path)}</span>
          </div>
          <div class="tree-subtext">${escapeHtml(s.path || '')}</div>
        `;
        item.addEventListener('click', () => {
          activeSelectedScript = s;
          listContainer.querySelectorAll('.tree-item').forEach(el => el.classList.remove('selected'));
          item.classList.add('selected');
          renderScriptDiff(projectId, s);
        });
        listContainer.appendChild(item);
      });

      if (!activeSelectedScript && scripts.length > 0) {
        activeSelectedScript = scripts[0];
        const first = listContainer.querySelector('.tree-item');
        if (first) first.classList.add('selected');
        renderScriptDiff(projectId, scripts[0]);
      }
    } catch (err) {
      listContainer.innerHTML = `<div class="error-note">Failed to load scripts: ${escapeHtml(err.message)}</div>`;
    }
  }

  async function renderScriptDiff(projectId, script) {
    const diffContainer = document.getElementById('script-diff-view');
    const diffTitle = document.getElementById('script-diff-title');
    if (!diffContainer || !script) return;

    if (diffTitle) diffTitle.textContent = `Script Diff: ${script.className || script.path}`;

    diffContainer.innerHTML = '<div class="spinner-small"></div> Computing diff...';
    try {
      const res = await fetch(`/api/studio/projects/${encodeURIComponent(projectId)}/scripts/diff`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          beforeContent: `// Original: ${script.className}\nusing UnityEngine;\n\npublic class ${script.className} : MonoBehaviour {\n    void Start() {}\n}`,
          afterContent: `// Modified: ${script.className}\nusing UnityEngine;\n\npublic class ${script.className} : MonoBehaviour {\n    public float moveSpeed = 5.0f;\n    void Start() {\n        Debug.Log("${script.className} initialized");\n    }\n}`
        })
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      const diffLines = data.diffLines || [];

      diffContainer.innerHTML = '';
      const pre = document.createElement('pre');
      pre.className = 'diff-code-block';

      diffLines.forEach(line => {
        const lineDiv = document.createElement('div');
        if (line.startsWith('+')) {
          lineDiv.className = 'diff-line diff-addition';
        } else if (line.startsWith('-')) {
          lineDiv.className = 'diff-line diff-deletion';
        } else {
          lineDiv.className = 'diff-line diff-context';
        }
        lineDiv.textContent = line;
        pre.appendChild(lineDiv);
      });
      diffContainer.appendChild(pre);
    } catch (err) {
      diffContainer.innerHTML = `<div class="error-note">Diff calculation failed: ${escapeHtml(err.message)}</div>`;
    }
  }

  // ── View 5: Asset Explorer ───────────────────────────────────────────────
  function setupStudioAssetFilter() {
    const chips = document.querySelectorAll('#asset-category-chips .chip');
    chips.forEach(chip => {
      chip.addEventListener('click', () => {
        chips.forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        activeAssetCategory = chip.getAttribute('data-cat') || '';
        loadProjectAssets(activeStudioProjectId, activeAssetCategory);
      });
    });
  }

  async function loadProjectAssets(projectId, category) {
    const grid = document.getElementById('assets-grid-container');
    if (!grid || !projectId) return;

    grid.innerHTML = '<div class="spinner-small"></div> Loading assets...';
    try {
      const url = category
        ? `/api/studio/projects/${encodeURIComponent(projectId)}/assets?category=${encodeURIComponent(category)}`
        : `/api/studio/projects/${encodeURIComponent(projectId)}/assets`;

      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const assets = await res.json();

      grid.innerHTML = '';
      if (!assets || assets.length === 0) {
        grid.innerHTML = '<div class="empty-note">No assets match the selected filter.</div>';
        return;
      }

      const iconMap = {
        'Prefab': '📦',
        'Material': '🎨',
        'Audio': '🎵',
        'Model': '🗿',
        'Texture': '🖼️',
        'Scene': '🏛️',
        'Script': '📝'
      };

      assets.forEach(a => {
        const card = document.createElement('div');
        card.className = 'asset-card';
        const icon = iconMap[a.type] || '📁';
        const name = a.path ? a.path.split(/[\\/]/).pop() : (a.name || 'Asset');
        card.innerHTML = `
          <div class="asset-icon">${icon}</div>
          <div class="asset-info">
            <h4 class="asset-name" title="${escapeHtml(name)}">${escapeHtml(name)}</h4>
            <span class="asset-type-badge">${escapeHtml(a.type || 'Asset')}</span>
            <span class="asset-path" title="${escapeHtml(a.path || '')}">${escapeHtml(a.path || '')}</span>
            <span class="asset-guid">GUID: ${escapeHtml((a.guid || '').substring(0, 12))}...</span>
          </div>
        `;
        grid.appendChild(card);
      });
    } catch (err) {
      grid.innerHTML = `<div class="error-note">Failed to load assets: ${escapeHtml(err.message)}</div>`;
    }
  }

  // ── View 6: Change Review & Human Lead Approval Boundary ─────────────────
  async function checkPendingChangesCount(projectId) {
    const badge = document.getElementById('pending-changes-badge');
    if (!badge || !projectId) return;

    try {
      const res = await fetch(`/api/studio/projects/${encodeURIComponent(projectId)}/changes/pending`);
      if (res.ok) {
        const pending = await res.json();
        const count = Array.isArray(pending) ? pending.length : 0;
        if (count > 0) {
          badge.textContent = count;
          badge.classList.remove('hidden');
        } else {
          badge.classList.add('hidden');
        }
      }
    } catch (ignored) {}
  }

  async function loadPendingChanges(projectId) {
    const listContainer = document.getElementById('changes-list-container');
    if (!listContainer || !projectId) return;

    listContainer.innerHTML = '<div class="spinner-small"></div> Loading change sets...';
    try {
      const res = await fetch(`/api/studio/projects/${encodeURIComponent(projectId)}/changes/pending`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const changeSets = await res.json();

      listContainer.innerHTML = '';
      checkPendingChangesCount(projectId);

      if (!changeSets || changeSets.length === 0) {
        listContainer.innerHTML = '<div class="empty-note">No changes pending review. All modifications approved or current workspace is clean.</div>';
        return;
      }

      changeSets.forEach(cs => {
        const item = document.createElement('div');
        const riskClass = `risk-${(cs.riskLevel || 'low').toLowerCase()}`;
        item.className = `change-set-card ${riskClass}`;

        const entries = cs.changes || [];
        item.innerHTML = `
          <div class="change-set-header">
            <div>
              <span class="change-set-id">ChangeSet #${escapeHtml(cs.changeSetId)}</span>
              <span class="run-id-pill">Run: ${escapeHtml(cs.agentRunId || 'N/A')}</span>
            </div>
            <span class="risk-badge ${riskClass}">Risk: ${escapeHtml(cs.riskLevel || 'LOW')}</span>
          </div>
          <div class="change-set-body">
            <p class="change-justification"><strong>Justification:</strong> ${escapeHtml(cs.justification || 'Autonomous agent modification')}</p>
            <div class="change-entries-list">
              ${entries.map(e => `
                <div class="change-entry-row">
                  <span class="change-type-pill pill-${(e.changeType || 'modified').toLowerCase()}">${escapeHtml(e.changeType || 'MODIFIED')}</span>
                  <span class="change-entry-path">${escapeHtml(e.filePath || '')}</span>
                  <span class="entry-risk">${escapeHtml(e.riskLevel || 'LOW')}</span>
                </div>
              `).join('')}
            </div>
          </div>
          <div class="change-set-actions">
            <button class="btn btn-small btn-success btn-approve-cs" data-id="${escapeHtml(cs.changeSetId)}">
              🛡️ Approve Changes
            </button>
            <button class="btn btn-small btn-danger btn-reject-cs" data-id="${escapeHtml(cs.changeSetId)}">
              ❌ Reject Changes
            </button>
          </div>
        `;

        // Wire approval
        const btnApprove = item.querySelector('.btn-approve-cs');
        btnApprove.addEventListener('click', async () => {
          btnApprove.disabled = true;
          try {
            const resp = await fetch(`/api/studio/changes/${encodeURIComponent(cs.changeSetId)}/approve`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ role: 'DEVELOPER', reviewerId: 'studio_lead' })
            });
            if (resp.ok) {
              alert(`ChangeSet #${cs.changeSetId} approved successfully.`);
              loadPendingChanges(projectId);
            } else {
              const err = await resp.json();
              alert(`Approval rejected: ${err.error || 'Permission denied or prohibited self-approval'}`);
            }
          } catch (e) {
            alert(`Approval failed: ${e.message}`);
          } finally {
            btnApprove.disabled = false;
          }
        });

        // Wire rejection
        const btnReject = item.querySelector('.btn-reject-cs');
        btnReject.addEventListener('click', async () => {
          const reason = prompt('Enter rejection reason / instructions for agent:');
          if (reason === null) return;
          btnReject.disabled = true;
          try {
            const resp = await fetch(`/api/studio/changes/${encodeURIComponent(cs.changeSetId)}/reject`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ reviewerId: 'studio_lead', reason: reason || 'Rejected by user' })
            });
            if (resp.ok) {
              alert(`ChangeSet #${cs.changeSetId} rejected.`);
              loadPendingChanges(projectId);
            }
          } catch (e) {
            alert(`Rejection failed: ${e.message}`);
          } finally {
            btnReject.disabled = false;
          }
        });

        listContainer.appendChild(item);
      });
    } catch (err) {
      listContainer.innerHTML = `<div class="error-note">Failed to load change review: ${escapeHtml(err.message)}</div>`;
    }
  }

  // ── View 7: Build & Export Center ─────────────────────────────────────────
  function setupStudioBuilds() {
    const btnStart = document.getElementById('btn-start-build');
    if (!btnStart) return;

    btnStart.addEventListener('click', async () => {
      const platformEl = document.getElementById('build-platform');
      const configEl = document.getElementById('build-config');
      const pathEl = document.getElementById('build-output-path');

      const platform = platformEl ? platformEl.value : 'StandaloneWindows64';
      const config = configEl ? configEl.value : 'Release';
      const outputPath = pathEl ? pathEl.value.trim() : 'Builds/Windows/Game.exe';

      btnStart.disabled = true;
      btnStart.textContent = 'Triggering Build...';

      try {
        const res = await fetch(`/api/studio/projects/${encodeURIComponent(activeStudioProjectId)}/builds`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ platform, configuration: config, outputPath })
        });

        if (res.ok) {
          const record = await res.json();
          alert(`Build queued successfully (ID: ${record.buildId}). Verification pipeline running.`);
          loadBuildHistory(activeStudioProjectId);
        } else {
          const err = await res.json();
          alert(`Build failed: ${err.error || 'Unknown error'}`);
        }
      } catch (e) {
        alert(`Build request error: ${e.message}`);
      } finally {
        btnStart.disabled = false;
        btnStart.textContent = '🚀 Trigger Build';
      }
    });
  }

  async function loadBuildHistory(projectId) {
    const container = document.getElementById('builds-history-container');
    if (!container || !projectId) return;

    container.innerHTML = '<div class="spinner-small"></div> Loading build records...';
    try {
      const res = await fetch(`/api/studio/projects/${encodeURIComponent(projectId)}/builds`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const builds = await res.json();

      container.innerHTML = '';
      if (!builds || builds.length === 0) {
        container.innerHTML = '<div class="empty-note">No builds recorded for this project yet. Trigger a build above.</div>';
        return;
      }

      builds.forEach(b => {
        const card = document.createElement('div');
        const statusClass = `status-${(b.status || 'queued').toLowerCase()}`;
        card.className = `build-history-card ${statusClass}`;

        const sizeStr = b.artifactSizeBytes ? `${(b.artifactSizeBytes / (1024 * 1024)).toFixed(1)} MB` : '0 MB';
        const durationStr = b.durationSeconds != null ? `${b.durationSeconds}s` : 'In progress';
        const existsIcon = b.artifactExists ? '✅ Exists' : '❌ Missing';

        card.innerHTML = `
          <div class="build-card-header">
            <div class="build-platform-info">
              <strong>${escapeHtml(b.platformTarget || 'StandaloneWindows64')}</strong>
              <span class="build-config-pill">${escapeHtml(b.buildConfiguration || 'Release')}</span>
            </div>
            <span class="badge ${statusClass}">${escapeHtml(b.status || 'QUEUED')}</span>
          </div>
          <div class="build-card-body">
            <div class="build-detail-row">
              <span><strong>Output:</strong> ${escapeHtml(b.outputPath || '')}</span>
              <span><strong>Artifact Verification:</strong> ${existsIcon} (${sizeStr})</span>
            </div>
            <div class="build-detail-row">
              <span><strong>Exit Code:</strong> ${b.exitCode != null ? b.exitCode : 'N/A'}</span>
              <span><strong>Duration:</strong> ${escapeHtml(durationStr)}</span>
            </div>
          </div>
        `;
        container.appendChild(card);
      });
    } catch (e) {
      container.innerHTML = `<div class="error-note">Failed to load build history: ${escapeHtml(e.message)}</div>`;
    }
  }

  // ── View 8: Centralized Diagnostics & Secret-Scrubbed Logs ────────────────
  function setupStudioDiagnosticsFilters() {
    const catSelect = document.getElementById('diag-filter-category');
    const sevSelect = document.getElementById('diag-filter-severity');
    const searchInput = document.getElementById('diag-search-input');

    if (catSelect) catSelect.addEventListener('change', () => loadStudioDiagnostics());
    if (sevSelect) sevSelect.addEventListener('change', () => loadStudioDiagnostics());
    if (searchInput) {
      let debounceTimer = null;
      searchInput.addEventListener('input', () => {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => loadStudioDiagnostics(), 300);
      });
    }
  }

  async function loadStudioDiagnostics() {
    const container = document.getElementById('diagnostics-stream-container');
    if (!container) return;

    const cat = document.getElementById('diag-filter-category')?.value || '';
    const sev = document.getElementById('diag-filter-severity')?.value || '';
    const search = document.getElementById('diag-search-input')?.value.trim() || '';

    const params = new URLSearchParams();
    if (activeStudioProjectId) params.append('projectId', activeStudioProjectId);
    if (cat) params.append('category', cat);
    if (sev) params.append('severity', sev);
    if (search) params.append('search', search);
    params.append('limit', '80');

    try {
      const res = await fetch(`/api/studio/diagnostics?${params.toString()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const logs = await res.json();

      container.innerHTML = '';
      if (!logs || logs.length === 0) {
        container.innerHTML = '<div class="empty-note">No diagnostics matching current filters.</div>';
        return;
      }

      logs.forEach(log => {
        const item = document.createElement('div');
        const sevClass = `diag-${(log.severity || 'info').toLowerCase()}`;
        item.className = `diag-stream-item ${sevClass}`;

        const timeStr = log.timestamp ? new Date(log.timestamp).toLocaleTimeString() : '';
        item.innerHTML = `
          <div class="diag-meta-bar">
            <span class="diag-timestamp">${escapeHtml(timeStr)}</span>
            <span class="diag-cat-pill cat-${(log.category || 'tools').toLowerCase()}">${escapeHtml(log.category || 'LOG')}</span>
            <span class="diag-sev-badge ${sevClass}">${escapeHtml(log.severity || 'INFO')}</span>
            <span class="diag-source">${escapeHtml(log.source || 'Studio')}</span>
          </div>
          <div class="diag-msg-body">${escapeHtml(log.message || '')}</div>
        `;
        container.appendChild(item);
      });
    } catch (err) {
      container.innerHTML = `<div class="error-note">Failed to load diagnostics: ${escapeHtml(err.message)}</div>`;
    }
  }

  // =========================================================================
  // Phase 12 — Release Management, Backups & Settings
  // =========================================================================

  function setupStudioReleases() {
    const btnCreateModal = document.getElementById('btn-create-release-modal');
    const btnCloseModal = document.getElementById('btn-close-release-modal');
    const btnCancelModal = document.getElementById('btn-cancel-release');
    const btnSubmit = document.getElementById('btn-submit-release');
    const btnRefresh = document.getElementById('btn-refresh-releases');
    const modal = document.getElementById('release-modal');

    if (btnCreateModal && modal) {
      btnCreateModal.addEventListener('click', () => modal.classList.remove('hidden'));
    }
    if (btnCloseModal && modal) {
      btnCloseModal.addEventListener('click', () => modal.classList.add('hidden'));
    }
    if (btnCancelModal && modal) {
      btnCancelModal.addEventListener('click', () => modal.classList.add('hidden'));
    }
    if (btnSubmit) {
      btnSubmit.addEventListener('click', handleCreateReleaseCandidate);
    }
    if (btnRefresh) {
      btnRefresh.addEventListener('click', () => loadReleases(activeStudioProjectId));
    }
  }

  async function loadReleases(projectId) {
    const list = document.getElementById('releases-list');
    if (!list) return;
    list.innerHTML = '<div class="loading-spinner">Loading releases...</div>';

    try {
      const res = await fetch(`/api/product/projects/${encodeURIComponent(projectId)}/releases`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const releases = await res.json();

      list.innerHTML = '';
      if (!releases || releases.length === 0) {
        list.innerHTML = '<div class="empty-note">No releases found for this project. Create a Release Candidate to begin.</div>';
        return;
      }

      releases.forEach(rel => {
        const card = document.createElement('div');
        const statusClass = (rel.status || 'draft').toLowerCase().replace(/_/g, '-');
        card.className = `release-card ${statusClass}`;

        const isPublished = rel.status === 'PUBLISHED';
        const isApproved = rel.status === 'APPROVED';
        const isReadyForReview = rel.status === 'READY_FOR_REVIEW';
        const isDraft = rel.status === 'DRAFT';

        card.innerHTML = `
          <div class="release-card-header">
            <div class="release-version-title">
              <span>v${escapeHtml(rel.versionString || '0.0.0')}</span>
              <span class="channel-pill channel-${(rel.channel || 'development').toLowerCase()}">${escapeHtml(rel.channel || 'DEV')}</span>
            </div>
            <span class="badge badge-${statusClass}">${escapeHtml(rel.status || 'DRAFT')}</span>
          </div>
          <div class="release-card-body">
            <div class="release-meta-row">
              <span>Release ID:</span>
              <code>${escapeHtml(rel.releaseId || '')}</code>
            </div>
            <div class="release-meta-row">
              <span>Created:</span>
              <span>${rel.createdAt ? new Date(rel.createdAt).toLocaleString() : 'N/A'}</span>
            </div>
            <div class="release-notes-box">
              <strong>Notes:</strong> ${escapeHtml(rel.changelog || 'No notes provided')}
            </div>
          </div>
          <div class="release-card-actions">
            ${(isDraft || isReadyForReview) ? `
              <button class="btn btn-small btn-secondary" onclick="window.approveReleaseCandidate('${escapeHtml(rel.releaseId)}')">
                🛡️ Approve (Human)
              </button>
            ` : ''}
            ${isApproved ? `
              <button class="btn btn-small btn-primary" onclick="window.publishReleaseCandidate('${escapeHtml(rel.releaseId)}')">
                🚀 Publish
              </button>
            ` : ''}
            ${isPublished ? `
              <button class="btn btn-small btn-danger" onclick="window.rollbackReleaseCandidate('${escapeHtml(rel.releaseId)}')">
                ⏮️ Rollback
              </button>
            ` : ''}
          </div>
        `;
        list.appendChild(card);
      });
    } catch (e) {
      list.innerHTML = `<div class="error-note">Failed to load releases: ${escapeHtml(e.message)}</div>`;
    }
  }

  async function handleCreateReleaseCandidate() {
    const version = document.getElementById('rel-version')?.value.trim();
    const channel = document.getElementById('rel-channel')?.value;
    const changelog = document.getElementById('rel-changelog')?.value.trim();
    const feedback = document.getElementById('rel-feedback');

    if (!version) {
      if (feedback) {
        feedback.textContent = 'Version string is required (e.g. 1.0.0)';
        feedback.classList.remove('hidden');
      }
      return;
    }

    try {
      const res = await fetch(`/api/product/projects/${encodeURIComponent(activeStudioProjectId)}/releases`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ version, channel, changelog })
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || `HTTP ${res.status}`);
      }
      document.getElementById('release-modal')?.classList.add('hidden');
      loadReleases(activeStudioProjectId);
    } catch (e) {
      if (feedback) {
        feedback.textContent = e.message;
        feedback.classList.remove('hidden');
      }
    }
  }

  window.approveReleaseCandidate = async function(releaseId) {
    if (!confirm('Approve release as Human Lead? Automated agents and LLMs are strictly forbidden from approving.')) return;
    try {
      const res = await fetch(`/api/product/releases/${encodeURIComponent(releaseId)}/approve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ role: 'OWNER', reviewerId: 'studio_lead', notes: 'Approved via Studio UI' })
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || `HTTP ${res.status}`);
      }
      loadReleases(activeStudioProjectId);
    } catch (e) {
      alert('Approval failed: ' + e.message);
    }
  };

  window.publishReleaseCandidate = async function(releaseId) {
    const artifactId = prompt('Enter Build Artifact ID to publish with this release (e.g. from Builds tab):');
    if (!artifactId) return;

    try {
      const res = await fetch(`/api/product/releases/${encodeURIComponent(releaseId)}/publish?projectId=${encodeURIComponent(activeStudioProjectId)}&artifactId=${encodeURIComponent(artifactId)}`, {
        method: 'POST'
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || `HTTP ${res.status}`);
      }
      alert('Release published successfully! Cryptographic signature verified and release locked.');
      loadReleases(activeStudioProjectId);
    } catch (e) {
      alert('Publish failed: ' + e.message);
    }
  };

  window.rollbackReleaseCandidate = async function(releaseId) {
    if (!confirm('Rollback this release? Status will be updated to ROLLED_BACK.')) return;
    try {
      const res = await fetch(`/api/product/releases/${encodeURIComponent(releaseId)}/rollback?projectId=${encodeURIComponent(activeStudioProjectId)}`, {
        method: 'POST'
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || `HTTP ${res.status}`);
      }
      loadReleases(activeStudioProjectId);
    } catch (e) {
      alert('Rollback failed: ' + e.message);
    }
  };

  // Backups & Packaging
  function setupStudioBackups() {
    const btnCreate = document.getElementById('btn-create-backup');
    const btnExport = document.getElementById('btn-export-package');
    const btnImport = document.getElementById('btn-import-package');
    const btnRefresh = document.getElementById('btn-refresh-backups');

    if (btnCreate) {
      btnCreate.addEventListener('click', () => handleCreateBackup(activeStudioProjectId));
    }
    if (btnExport) {
      btnExport.addEventListener('click', () => handleExportPackage(activeStudioProjectId));
    }
    if (btnImport) {
      btnImport.addEventListener('click', handleImportPackage);
    }
    if (btnRefresh) {
      btnRefresh.addEventListener('click', () => loadBackups(activeStudioProjectId));
    }
  }

  async function loadBackups(projectId) {
    const tbody = document.getElementById('backups-tbody');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="6" class="text-center">Loading backups...</td></tr>';

    try {
      const res = await fetch(`/api/product/projects/${encodeURIComponent(projectId)}/backups`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const backups = await res.json();

      tbody.innerHTML = '';
      if (!backups || backups.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No backups found for this project.</td></tr>';
        return;
      }

      backups.forEach(b => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td><code>${escapeHtml(b.backupId || '')}</code></td>
          <td>${escapeHtml(b.projectId || '')}</td>
          <td>${escapeHtml(b.archivePath || '')}</td>
          <td><code>${escapeHtml((b.checksum || '').substring(0, 16))}...</code></td>
          <td>${b.createdAt ? new Date(b.createdAt).toLocaleString() : 'N/A'}</td>
          <td>
            <button class="btn btn-small btn-secondary" onclick="window.restoreStudioBackup('${escapeHtml(b.backupId)}')">
              ⏮️ Restore
            </button>
          </td>
        `;
        tbody.appendChild(tr);
      });
    } catch (e) {
      tbody.innerHTML = `<tr><td colspan="6" class="text-center error-text">Failed to load backups: ${escapeHtml(e.message)}</td></tr>`;
    }
  }

  async function handleCreateBackup(projectId) {
    try {
      const res = await fetch(`/api/product/projects/${encodeURIComponent(projectId)}/backups`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({})
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || `HTTP ${res.status}`);
      }
      alert('Backup created successfully! Zero-secret guarantee verified.');
      loadBackups(projectId);
    } catch (e) {
      alert('Backup creation failed: ' + e.message);
    }
  }

  window.restoreStudioBackup = async function(backupId) {
    if (!confirm(`Restore project from backup ${backupId}? Current workspace files may be replaced.`)) return;
    try {
      const res = await fetch(`/api/product/projects/${encodeURIComponent(activeStudioProjectId)}/backups/${encodeURIComponent(backupId)}/restore`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({})
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || `HTTP ${res.status}`);
      }
      const data = await res.json();
      alert(`Restore result: ${data.status} — ${data.summary}`);
    } catch (e) {
      alert('Restore failed: ' + e.message);
    }
  };

  async function handleExportPackage(projectId) {
    try {
      const res = await fetch(`/api/product/projects/${encodeURIComponent(projectId)}/package/export`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({})
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || `HTTP ${res.status}`);
      }
      const data = await res.json();
      alert(`Project successfully exported to portable package: ${data.exportedFile}`);
    } catch (e) {
      alert('Export failed: ' + e.message);
    }
  }

  async function handleImportPackage() {
    const path = prompt('Enter path to .autonomous-project package file to import:');
    if (!path) return;

    try {
      const res = await fetch('/api/product/projects/package/import', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ packageFile: path })
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || `HTTP ${res.status}`);
      }
      alert('Project package imported successfully!');
      loadStudioProjects();
    } catch (e) {
      alert('Import failed: ' + e.message);
    }
  }

  // Settings & Capabilities
  function setupStudioSettings() {
    const btnDiagExport = document.getElementById('btn-export-diagnostics-bundle');
    if (btnDiagExport) {
      btnDiagExport.addEventListener('click', handleExportDiagnosticsBundle);
    }
  }

  async function loadSettingsView(projectId) {
    // 1. Platform capabilities
    const capList = document.getElementById('platform-capabilities-list');
    if (capList) {
      capList.innerHTML = '<div>Scanning capabilities...</div>';
      try {
        const res = await fetch(`/api/product/projects/${encodeURIComponent(projectId)}/capabilities`);
        if (res.ok) {
          const caps = await res.json();
          capList.innerHTML = '';
          Object.values(caps).forEach(cap => {
            const chip = document.createElement('div');
            chip.className = `capability-chip ${cap.buildTargetAvailable ? 'capability-supported' : 'capability-unsupported'}`;
            chip.innerHTML = `
              <span>${escapeHtml(cap.platform || '')}</span>
              <span>${cap.buildTargetAvailable ? '✅ Ready' : '❌ Missing'}</span>
            `;
            capList.appendChild(chip);
          });
        }
      } catch (e) {
        capList.innerHTML = `<div class="error-note">Failed: ${escapeHtml(e.message)}</div>`;
      }
    }

    // 2. Configuration profiles
    const profList = document.getElementById('configuration-profiles-list');
    if (profList) {
      profList.innerHTML = '<div>Loading profiles...</div>';
      try {
        const res = await fetch(`/api/product/projects/${encodeURIComponent(projectId)}/configurations`);
        if (res.ok) {
          const profs = await res.json();
          profList.innerHTML = '';
          if (!profs || profs.length === 0) {
            profList.innerHTML = '<div class="empty-note">No profiles configured. Profiles strictly enforce zero-secret storage.</div>';
          } else {
            profs.forEach(p => {
              const chip = document.createElement('div');
              chip.className = 'profile-chip';
              chip.innerHTML = `
                <div>
                  <strong>${escapeHtml(p.profileName || '')}</strong>
                  <span class="text-muted">(${escapeHtml(p.environment || '')})</span>
                </div>
                <code>${escapeHtml(p.profileId || '')}</code>
              `;
              profList.appendChild(chip);
            });
          }
        }
      } catch (e) {
        profList.innerHTML = `<div class="error-note">Failed: ${escapeHtml(e.message)}</div>`;
      }
    }
  }

  async function handleExportDiagnosticsBundle() {
    try {
      const res = await fetch('/api/product/diagnostics/export', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({})
      });
      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || `HTTP ${res.status}`);
      }
      const data = await res.json();
      alert(`Diagnostics bundle successfully exported to: ${data.bundlePath}`);
    } catch (e) {
      alert('Export failed: ' + e.message);
    }
  }

  // --- Wizard Modal Logic ---

  async function openWizardModal() {
    if (!wizardModal) return;
    wizardModal.classList.remove('hidden');
    if (wizProvFeedback) wizProvFeedback.classList.add('hidden');
    if (wizSecFeedback) wizSecFeedback.classList.add('hidden');
    if (wizApiKey) wizApiKey.value = '';
    await refreshWizardStatus();
  }

  function closeWizardModal() {
    if (!wizardModal) return;
    wizardModal.classList.add('hidden');
  }

  async function refreshWizardStatus() {
    try {
      const res = await fetch('/api/product/setup-status');
      if (!res.ok) throw new Error('Failed to load setup status');
      const status = await res.json();

      // 1. System Requirements
      if (status.systemCheck) {
        const sc = status.systemCheck;
        if (wizSysBadge) {
          wizSysBadge.textContent = sc.satisfied ? 'PASSED' : 'ACTION REQUIRED';
          wizSysBadge.className = 'badge ' + (sc.satisfied ? 'badge-success' : 'badge-error');
        }
        if (wizJava) wizJava.textContent = `Java ${sc.javaVersion || 0} (${sc.javaVersion >= 21 ? 'OK' : 'Requires Java 21+'})`;
        if (wizMemory) wizMemory.textContent = `${Math.round((sc.maxMemoryBytes || 0) / (1024 * 1024))} MB`;
        if (wizDisk) wizDisk.textContent = `${Math.round((sc.freeDiskBytes || 0) / (1024 * 1024 * 1024))} GB`;
        if (wizWrite) wizWrite.textContent = sc.filesystemWritable ? 'Writable' : 'Read-Only';
        if (wizSqlite) wizSqlite.textContent = sc.sqliteSupported ? 'Available (JDBC)' : 'Unavailable';
      }

      // 2. AI Provider
      if (wizProvBadge) {
        wizProvBadge.textContent = status.providerState || 'UNCONFIGURED';
        const isOk = status.providerState === 'VERIFIED' || status.providerState === 'CONFIGURED';
        wizProvBadge.className = 'badge ' + (isOk ? 'badge-success' : 'badge-warning');
      }

      // 3. Unity Connection
      if (wizUnityBadge) {
        wizUnityBadge.textContent = status.unityState || 'NOT_CONNECTED';
        const isConn = status.unityState === 'CONNECTED';
        wizUnityBadge.className = 'badge ' + (isConn ? 'badge-success' : 'badge-error');
      }
      if (wizUnityState) wizUnityState.textContent = status.unityState || 'NOT_CONNECTED';
      if (wizUnityProjects) wizUnityProjects.textContent = String(status.connectedProjectCount || 0);

      // 4. Overall Readiness
      const isReady = status.readinessState === 'READY_FOR_AUTONOMOUS_RUN';
      if (wizReadinessBanner) {
        wizReadinessBanner.className = 'wizard-status-banner ' + (isReady ? 'ready' : 'not-ready');
      }
      if (wizReadinessTitle) {
        wizReadinessTitle.textContent = isReady ? 'Studio Status: READY FOR AUTONOMOUS RUN' : 'Studio Status: NOT READY';
      }
      if (wizReadinessDesc) {
        if (isReady) {
          wizReadinessDesc.textContent = 'All core prerequisites are satisfied. Ready to create and launch autonomous games.';
        } else if (status.missingPrerequisites && status.missingPrerequisites.length > 0) {
          wizReadinessDesc.textContent = 'Missing: ' + status.missingPrerequisites.join(', ');
        } else {
          wizReadinessDesc.textContent = 'Please configure missing requirements above.';
        }
      }
    } catch (e) {
      console.warn('Error refreshing setup status:', e);
    }
  }

  async function handleWizardTestProvider() {
    if (!wizProvFeedback) return;
    wizProvFeedback.classList.remove('hidden', 'success', 'error');
    wizProvFeedback.textContent = 'Testing connection...';

    const providerName = wizProviderSelect ? wizProviderSelect.value : 'nvidia';
    const apiKeyOverride = wizApiKey ? wizApiKey.value.trim() : '';

    try {
      const res = await fetch('/api/product/test-provider', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          providerName: providerName,
          apiKeyOverride: apiKeyOverride || null
        })
      });

      const result = await res.json();
      if (result.success) {
        wizProvFeedback.className = 'settings-feedback success';
        wizProvFeedback.textContent = `Success (${result.latencyMs}ms): ${result.message}`;
        await refreshWizardStatus();
      } else {
        wizProvFeedback.className = 'settings-feedback error';
        wizProvFeedback.textContent = `Connection failed (${result.statusCode}): ${result.message}`;
      }
    } catch (e) {
      wizProvFeedback.className = 'settings-feedback error';
      wizProvFeedback.textContent = 'Error testing provider: ' + e.message;
    }
  }

  async function handleWizardRunSecurityAudit() {
    if (!wizSecFeedback) return;
    wizSecFeedback.classList.remove('hidden', 'success', 'error');
    wizSecFeedback.textContent = 'Scanning for credentials and plaintext secrets...';

    try {
      const res = await fetch('/api/product/security-audit');
      const audit = await res.json();

      if (audit.clean) {
        wizSecFeedback.className = 'settings-feedback success';
        wizSecFeedback.textContent = `Audit Clean! Scanned ${audit.scannedFiles} files in ${audit.durationMs}ms. Zero secret leaks detected.`;
        if (wizSecBadge) {
          wizSecBadge.textContent = 'Clean';
          wizSecBadge.className = 'badge badge-success';
        }
      } else {
        wizSecFeedback.className = 'settings-feedback error';
        wizSecFeedback.textContent = `Violations detected (${audit.violations?.length || 0}): ` + (audit.violations || []).join('; ');
        if (wizSecBadge) {
          wizSecBadge.textContent = 'Violations';
          wizSecBadge.className = 'badge badge-error';
        }
      }
    } catch (e) {
      wizSecFeedback.className = 'settings-feedback error';
      wizSecFeedback.textContent = 'Audit request failed: ' + e.message;
    }
  }

})();


