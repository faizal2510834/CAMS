/**
 * CAMS Foundation - Round-trip Proof-of-life Interactive Controller
 */

document.addEventListener('DOMContentLoaded', () => {
  const pingBtn = document.getElementById('pingBtn');
  const pingInput = document.getElementById('pingInput');
  const pingSpinner = document.getElementById('pingSpinner');
  const statusPill = document.getElementById('statusPill');
  const terminal = document.getElementById('terminalOutput');
  const latencyBadge = document.getElementById('latencyBadge');
  const dbStatusVal = document.getElementById('dbStatusVal');

  const nodes = [
    document.getElementById('node-browser'),
    document.getElementById('node-servlet'),
    document.getElementById('node-service'),
    document.getElementById('node-dao'),
    document.getElementById('node-jdbc'),
    document.getElementById('node-oracle')
  ];

  function setStatus(type, text) {
    statusPill.className = 'status-pill ' + type;
    statusPill.textContent = text;
    if (dbStatusVal) {
      dbStatusVal.textContent = text;
      dbStatusVal.style.color = (type === 'success') ? '#34d399' : (type === 'warning') ? '#fbbf24' : '#f87171';
    }
  }

  function highlightPipeline(stepIndex, isSuccess = true) {
    nodes.forEach((node, i) => {
      if (!node) return;
      if (i <= stepIndex) {
        node.classList.add(isSuccess ? 'success' : 'active');
      } else {
        node.classList.remove('success', 'active');
      }
    });
  }

  function resetPipeline() {
    nodes.forEach(node => {
      if (node) node.classList.remove('success', 'active');
    });
  }

  async function runRoundTripTest() {
    const customMessage = pingInput.value.trim() || 'CAMS JDBC Verification';
    pingBtn.disabled = true;
    pingSpinner.style.display = 'inline-block';
    setStatus('warning', 'Connecting...');
    resetPipeline();

    // Visual sequence
    let currentStep = 0;
    const stepInterval = setInterval(() => {
      if (currentStep < nodes.length - 1) {
        highlightPipeline(currentStep, false);
        currentStep++;
      }
    }, 90);

    const startTime = performance.now();

    try {
      terminal.textContent = `[HTTP REQUEST] GET /api/ping?message=${encodeURIComponent(customMessage)}\n`;
      terminal.textContent += `[PIPELINE] Browser -> PingServlet -> PingService -> PingDAO -> JDBC -> Oracle...\n`;

      const response = await fetch(`/api/ping?message=${encodeURIComponent(customMessage)}`, {
        method: 'GET',
        headers: {
          'Accept': 'application/json'
        }
      });

      const totalTime = Math.round(performance.now() - startTime);
      clearInterval(stepInterval);

      const data = await response.json();
      latencyBadge.textContent = `${totalTime} ms`;

      if (response.ok && data.success) {
        setStatus('success', 'CONNECTED');
        highlightPipeline(nodes.length - 1, true);

        terminal.textContent += `[HTTP STATUS] 200 OK (${totalTime}ms total roundtrip)\n\n`;
        terminal.textContent += JSON.stringify(data, null, 2);
      } else {
        setStatus(response.status === 503 ? 'warning' : 'error', response.status === 503 ? 'CONFIG PENDING' : 'FAILED');
        highlightPipeline(3, false); // Stalled before DB

        terminal.textContent += `[HTTP STATUS] ${response.status} ${response.statusText} (${totalTime}ms)\n\n`;
        terminal.textContent += JSON.stringify(data, null, 2);

        if (response.status === 503 || (data.message && data.message.includes('password'))) {
          terminal.textContent += `\n\n[ACTION REQUIRED] Open src/main/resources/db.properties and set your Oracle password in 'db.password', then re-test.`;
        }
      }
    } catch (err) {
      clearInterval(stepInterval);
      const totalTime = Math.round(performance.now() - startTime);
      setStatus('error', 'SERVER UNREACHABLE');
      resetPipeline();

      terminal.textContent += `\n[NETWORK ERROR] Failed to connect to server: ${err.message}\n`;
      terminal.textContent += `Ensure the CAMS Tomcat server is running on http://localhost:8080.`;
    } finally {
      pingBtn.disabled = false;
      pingSpinner.style.display = 'none';
    }
  }

  pingBtn.addEventListener('click', runRoundTripTest);

  // Auto-run initial check on page load
  runRoundTripTest();
});
