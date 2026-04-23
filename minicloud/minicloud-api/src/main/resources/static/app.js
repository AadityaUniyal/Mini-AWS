// MiniCloud Console logic
const API_URL = '';

let currentUser = JSON.parse(localStorage.getItem('minicloud_user'));
let jwtToken = localStorage.getItem('minicloud_token');

// ─────────────────────────── Boot ───────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    if (jwtToken) {
        showDashboard();
    } else {
        showLogin();
    }

    // Nav Bindings
    document.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            document.querySelector('.nav-item.active').classList.remove('active');
            item.classList.add('active');
            loadView(item.dataset.view);
        });
    });

    // Refresh dashboard
    const refreshBtn = document.getElementById('refresh-dashboard');
    if (refreshBtn) refreshBtn.addEventListener('click', () => loadView('dashboard'));
});

// ─────────────────────────── Auth ───────────────────────────
document.getElementById('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    try {
        const response = await fetch('/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`
        });

        const data = await response.json();
        if (response.ok && data.token) {
            localStorage.setItem('minicloud_token', data.token);
            localStorage.setItem('minicloud_user', JSON.stringify(data));
            jwtToken = data.token;
            currentUser = data;
            showDashboard();
        } else {
            document.getElementById('login-error').innerText = data.message || 'Invalid credentials';
            document.getElementById('login-error').classList.remove('hidden');
        }
    } catch (err) {
        document.getElementById('login-error').innerText = 'Connection error';
        document.getElementById('login-error').classList.remove('hidden');
    }
});

document.getElementById('logout-btn').addEventListener('click', () => {
    localStorage.removeItem('minicloud_token');
    localStorage.removeItem('minicloud_user');
    location.reload();
});

function showLogin() {
    document.getElementById('login-view').classList.remove('hidden');
    document.getElementById('main-view').classList.add('hidden');
}

function showDashboard() {
    document.getElementById('login-view').classList.add('hidden');
    document.getElementById('main-view').classList.remove('hidden');
    document.getElementById('display-user').innerText = currentUser.username;
    loadView('dashboard');
    
    // Live metrics polling
    setInterval(() => {
        if (document.querySelector('.nav-item.active').dataset.view === 'dashboard') {
            updateDashboardStats();
        }
    }, 5000);
}

// ─────────────────────────── Views ───────────────────────────
async function loadView(view) {
    const area = document.getElementById('content-area');
    
    switch (view) {
        case 'dashboard':
            await updateDashboardStats();
            await updateRecentEvents();
            break;
        case 'audit':
            area.innerHTML = `<h1>Audit Logs</h1><p style="margin-bottom:20px;">Full CloudTrail-style activity tracking for all services.</p>
                             <div class="table-container"><table id="audit-table"><thead><tr><th>Time</th><th>Service</th><th>Action</th><th>Resource</th><th>User</th></tr></thead><tbody id="audit-body"></tbody></table></div>`;
            loadAuditLogs();
            break;
        case 'cloudwatch':
            area.innerHTML = `<h1>CloudWatch Monitoring</h1><p>Real-time resource performance metrics.</p>
                             <div class="stats-grid" id="metrics-grid"></div>`;
            loadLiveMetrics();
            break;
        default:
            area.innerHTML = `<h1>${view.toUpperCase()}</h1><p>Feature integration for ${view} is in progress.</p>`;
    }
}

// ─────────────────────────── Data Fetching ───────────────────────────
async function updateDashboardStats() {
    try {
        const fetchWithAuth = (url) => fetch(url, { headers: { 'Authorization': `Bearer ${jwtToken}` } }).then(res => res.json());
        
        const [ec2, s3, rds, metrics] = await Promise.all([
            fetchWithAuth('/compute/instances'),
            fetchWithAuth('/storage/buckets/user/' + currentUser.username), // Note: updated to use username if that's what API expects
            fetchWithAuth('/rds/instances'),
            fetchWithAuth('/cloudwatch/metrics')
        ]);

        if (ec2.data) document.getElementById('stat-ec2-count').innerText = ec2.data.filter(i => i.state === 'RUNNING').length;
        if (s3.data) document.getElementById('stat-s3-count').innerText = s3.data.length;
        if (rds.data) document.getElementById('stat-rds-count').innerText = rds.data.length;
        if (metrics) document.getElementById('stat-cpu-load').innerText = metrics.cpuLoad.toFixed(1) + '%';
        
    } catch (e) { console.error('Dashboard stats error', e); }
}

async function updateRecentEvents() {
    const tableBody = document.querySelector('#recent-events-table tbody');
    try {
        const res = await fetch('/cloudwatch/audit/recent', { headers: { 'Authorization': `Bearer ${jwtToken}` } });
        const data = await res.json();
        if (data.data) {
            tableBody.innerHTML = data.data.slice(0, 10).map(log => `
                <tr>
                    <td style="color:var(--aws-gray-text); font-size:12px;">${new Date(log.timestamp).toLocaleString()}</td>
                    <td><span class="badge badge-info">${log.service}</span></td>
                    <td style="font-weight:600;">${log.action}</td>
                    <td style="font-family:monospace;">${log.resourceId}</td>
                    <td><span class="badge badge-success">OK</span></td>
                </tr>
            `).join('');
        }
    } catch (e) { tableBody.innerHTML = '<tr><td colspan="5">Error loading events</td></tr>'; }
}

async function loadAuditLogs() {
    const body = document.getElementById('audit-body');
    const res = await fetch('/cloudwatch/audit/recent', { headers: { 'Authorization': `Bearer ${jwtToken}` } });
    const data = await res.json();
    if (data.data) {
        body.innerHTML = data.data.map(log => `
            <tr>
                <td>${new Date(log.timestamp).toLocaleString()}</td>
                <td>${log.service}</td>
                <td>${log.action}</td>
                <td>${log.resourceId}</td>
                <td style="font-weight:600;">${log.username}</td>
            </tr>
        `).join('');
    }
}

async function loadLiveMetrics() {
    const grid = document.getElementById('metrics-grid');
    const res = await fetch('/cloudwatch/metrics', { headers: { 'Authorization': `Bearer ${jwtToken}` } });
    const data = await res.json();
    
    grid.innerHTML = `
        <div class="stat-card">
            <span class="stat-label">CPU Utilization</span>
            <span class="stat-value">${data.cpuLoad.toFixed(2)}%</span>
            <div style="height:4px; width:100%; background:#eee; margin-top:10px;">
                <div style="height:100%; width:${Math.min(100, data.cpuLoad)}%; background:var(--aws-orange);"></div>
            </div>
        </div>
        <div class="stat-card">
            <span class="stat-label">Heap Memory Used</span>
            <span class="stat-value">${data.usedHeapMb.toFixed(0)} MB</span>
        </div>
        <div class="stat-card">
            <span class="stat-label">Active JVM Threads</span>
            <span class="stat-value">${data.activeThreads}</span>
        </div>
        <div class="stat-card">
            <span class="stat-label">System Uptime</span>
            <span class="stat-value">${(data.uptimeSeconds / 3600).toFixed(1)} hrs</span>
        </div>
    `;
}
