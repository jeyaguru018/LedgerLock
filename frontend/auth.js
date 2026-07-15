// auth.js - Security Interceptor and Token Management

// API Base configuration for split-origin deployments
// 1. Production: loaded from config.js (window.ENV.API_BASE_URL)
// 2. Local Dev: fallback to DEV_API_BASE_URL in localStorage if present
const API_BASE = (typeof window.ENV !== 'undefined' && window.ENV.API_BASE_URL)
    ? window.ENV.API_BASE_URL 
    : (localStorage.getItem('DEV_API_BASE_URL') || '');

// 1. Immediate unauthenticated route shielding
(function () {
    const token = localStorage.getItem('accessToken');
    const path = window.location.pathname;
    const isPublicPage = path === '/' || path.endsWith('index.html') || path.endsWith('login.html') || path.endsWith('signup.html');
    
    if (!token && !isPublicPage) {
        window.location.replace('/login.html');
    }
})();

// 2. Clear authentication context and call logout endpoint
async function logout() {
    const token = localStorage.getItem('accessToken');
    if (token) {
        try {
            await fetch(API_BASE + '/api/auth/logout', {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });
        } catch (e) {
            console.error("Logout endpoint request failed: ", e);
        }
    }
    localStorage.clear();
    window.location.replace('/login.html');
}

// 3. Authenticated Fetch wrapper with automatic JWT token rotation (refresh flow)
async function authenticatedFetch(url, options = {}) {
    let token = localStorage.getItem('accessToken');
    options.headers = options.headers || {};
    options.headers['Authorization'] = `Bearer ${token}`;

    const targetUrl = url.startsWith('http') ? url : (API_BASE + url);
    let response = await fetch(targetUrl, options);

    if (response.status === 401) {
        // Access token expired, attempt to refresh
        const refreshToken = localStorage.getItem('refreshToken');
        if (!refreshToken) {
            logout();
            return response;
        }

        try {
            const refreshResponse = await fetch(API_BASE + '/api/auth/refresh', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ refreshToken })
            });

            if (refreshResponse.ok) {
                const data = await refreshResponse.json();
                localStorage.setItem('accessToken', data.accessToken);
                localStorage.setItem('refreshToken', data.refreshToken);
                
                // Retry the original request with the new rotated token
                options.headers['Authorization'] = `Bearer ${data.accessToken}`;
                response = await fetch(targetUrl, options);
            } else {
                // Refresh token invalid or expired (e.g. 7 days limit exceeded)
                logout();
            }
        } catch (err) {
            console.error("Token rotation failed: ", err);
            logout();
        }
    }

    // Idempotency conflict / Race condition retry (up to 2 times)
    let idempotencyAttempts = 0;
    while (response.status === 409 && idempotencyAttempts < 2) {
        idempotencyAttempts++;
        console.warn(`Idempotency conflict (409) detected. Retrying attempt ${idempotencyAttempts}...`);
        await new Promise(resolve => setTimeout(resolve, 250)); // 250ms backoff
        response = await fetch(targetUrl, options); // Exact same options (including X-Idempotency-Key and body)
    }

    return response;
}

// 4. Dom bindings for shared layout components
document.addEventListener('DOMContentLoaded', () => {
    // Bind authenticated user email dynamically to profile indicators
    const emailElements = document.querySelectorAll('.user-email');
    const storedEmail = localStorage.getItem('email');
    if (storedEmail) {
        emailElements.forEach(el => {
            el.textContent = storedEmail;
        });
    }

    // Bind logout triggers
    const logoutButtons = document.querySelectorAll('.logout-btn');
    logoutButtons.forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            logout();
        });
    });

    // Initialize Navbar Dropdowns
    setupNavbarDropdowns();
});

function setupNavbarDropdowns() {
    const notifBtn = document.getElementById('nav-notification-btn');
    const notifDropdown = document.getElementById('nav-notification-dropdown');
    const accountBtn = document.getElementById('nav-account-btn');
    const accountDropdown = document.getElementById('nav-account-dropdown');

    if (notifBtn && notifDropdown) {
        notifBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            notifDropdown.classList.toggle('hidden');
            if (accountDropdown) accountDropdown.classList.add('hidden');
        });
    }

    if (accountBtn && accountDropdown) {
        accountBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            accountDropdown.classList.toggle('hidden');
            if (notifDropdown) notifDropdown.classList.add('hidden');
        });
    }

    // Close dropdowns when clicking outside
    document.addEventListener('click', () => {
        if (notifDropdown && !notifDropdown.classList.contains('hidden')) {
            notifDropdown.classList.add('hidden');
        }
        if (accountDropdown && !accountDropdown.classList.contains('hidden')) {
            accountDropdown.classList.add('hidden');
        }
    });

    // Prevent closing when clicking inside the dropdowns
    if (notifDropdown) {
        notifDropdown.addEventListener('click', (e) => e.stopPropagation());
    }
    if (accountDropdown) {
        accountDropdown.addEventListener('click', (e) => e.stopPropagation());
    }
}
