/**
 * Galaxy Simulator Client-Side JavaScript
 * Handles visualization, controls, and WebSocket communication
 */

class GalaxySimulator {
    constructor() {
        this.canvas = document.getElementById('galaxyCanvas');
        this.ctx = this.canvas.getContext('2d');
        this.width = this.canvas.width;
        this.height = this.canvas.height;
        
        // Visualization parameters
        this.zoom = 1.0;
        this.offsetX = 0;
        this.offsetY = 0;
        this.showQuadtree = false;
        this.particleSize = 2;
        
        // Simulation state
        this.simulationData = null;
        this.isConnected = false;
        
        // Time scale options (in years per second)
        this.timeScales = [1, 10, 100, 1000, 1e4, 1e5, 1e6, 1e7, 1e8];
        
        // WebSocket connection
        this.stompClient = null;
        
        // Initialize
        this.initializeControls();
        this.connectWebSocket();
        this.startRenderLoop();
    }
    
    /**
     * Initialize UI controls
     */
    initializeControls() {
        // Buttons
        $('#btnInitialize').click(() => this.initialize());
        $('#btnStart').click(() => this.start());
        $('#btnPause').click(() => this.pause());
        $('#btnReset').click(() => this.reset());
        
        // Sliders
        $('#particleCountSlider').on('input', (e) => {
            $('#particleCountLabel').text(e.target.value);
        });
        
        $('#timeScaleSlider').on('input', (e) => {
            const scale = this.timeScales[e.target.value];
            this.setTimeScale(scale);
        });
        
        $('#zoomSlider').on('input', (e) => {
            this.zoom = parseFloat(e.target.value);
            $('#zoomLabel').text(this.zoom.toFixed(1) + 'x');
        });
        
        // Toggles
        $('#fastForwardToggle').change((e) => {
            this.toggleFastForward(e.target.checked);
        });
        
        $('#barToggle').change((e) => {
            this.toggleBar(e.target.checked);
        });
        
        $('#quadtreeToggle').change((e) => {
            this.showQuadtree = e.target.checked;
        });
        
        // Canvas mouse controls
        this.setupCanvasControls();
    }
    
    /**
     * Setup canvas pan and zoom controls
     */
    setupCanvasControls() {
        let isDragging = false;
        let lastX = 0;
        let lastY = 0;
        
        this.canvas.addEventListener('mousedown', (e) => {
            isDragging = true;
            lastX = e.clientX;
            lastY = e.clientY;
        });
        
        this.canvas.addEventListener('mousemove', (e) => {
            if (isDragging) {
                const dx = e.clientX - lastX;
                const dy = e.clientY - lastY;
                this.offsetX += dx / this.zoom;
                this.offsetY += dy / this.zoom;
                lastX = e.clientX;
                lastY = e.clientY;
            }
        });
        
        this.canvas.addEventListener('mouseup', () => {
            isDragging = false;
        });
        
        this.canvas.addEventListener('wheel', (e) => {
            e.preventDefault();
            const delta = e.deltaY > 0 ? 0.9 : 1.1;
            this.zoom *= delta;
            this.zoom = Math.max(0.5, Math.min(10, this.zoom));
            $('#zoomSlider').val(this.zoom);
            $('#zoomLabel').text(this.zoom.toFixed(1) + 'x');
        });
    }
    
    /**
     * Connect to WebSocket for real-time updates
     */
    connectWebSocket() {
        const socket = new SockJS('/ws-galaxy');
        this.stompClient = Stomp.over(socket);
        
        this.stompClient.connect({}, (frame) => {
            console.log('Connected to WebSocket');
            this.isConnected = true;
            
            // Subscribe to simulation updates
            this.stompClient.subscribe('/topic/simulation', (message) => {
                this.simulationData = JSON.parse(message.body);
                this.updateStatus();
            });
        }, (error) => {
            console.error('WebSocket connection error:', error);
            this.isConnected = false;
            setTimeout(() => this.connectWebSocket(), 5000); // Retry after 5 seconds
        });
    }
    
    /**
     * API Calls
     */
    async initialize() {
        const particleCount = parseInt($('#particleCountSlider').val());
        try {
            const response = await fetch('/api/simulation/initialize?particleCount=' + particleCount, {
                method: 'POST'
            });
            const data = await response.json();
            console.log('Initialized:', data);
        } catch (error) {
            console.error('Error initializing:', error);
        }
    }
    
    async start() {
        try {
            const response = await fetch('/api/simulation/start', { method: 'POST' });
            const data = await response.json();
            console.log('Started:', data);
        } catch (error) {
            console.error('Error starting:', error);
        }
    }
    
    async pause() {
        try {
            const response = await fetch('/api/simulation/pause', { method: 'POST' });
            const data = await response.json();
            console.log('Paused:', data);
        } catch (error) {
            console.error('Error pausing:', error);
        }
    }
    
    async reset() {
        try {
            const response = await fetch('/api/simulation/reset', { method: 'POST' });
            const data = await response.json();
            console.log('Reset:', data);
            this.offsetX = 0;
            this.offsetY = 0;
            this.zoom = 1.0;
            $('#zoomSlider').val(1.0);
            $('#zoomLabel').text('1.0x');
        } catch (error) {
            console.error('Error resetting:', error);
        }
    }
    
    async setTimeScale(scale) {
        try {
            const response = await fetch('/api/simulation/timescale?scale=' + scale, {
                method: 'POST'
            });
            const data = await response.json();
            $('#timeScaleLabel').text(this.formatTimeScale(scale));
        } catch (error) {
            console.error('Error setting time scale:', error);
        }
    }
    
    async toggleFastForward(enabled) {
        try {
            const response = await fetch('/api/simulation/fastforward?enabled=' + enabled, {
                method: 'POST'
            });
            const data = await response.json();
            console.log('Fast forward:', data);
        } catch (error) {
            console.error('Error toggling fast forward:', error);
        }
    }
    
    async toggleBar(enabled) {
        try {
            const response = await fetch('/api/simulation/bar?enabled=' + enabled, {
                method: 'POST'
            });
            const data = await response.json();
            console.log('Bar potential:', data);
        } catch (error) {
            console.error('Error toggling bar:', error);
        }
    }
    
    /**
     * Update status display
     */
    updateStatus() {
        if (!this.simulationData) return;
        
        const data = this.simulationData;
        
        // Update status indicator
        const indicator = $('#statusIndicator');
        const statusText = $('#statusText');
        
        if (data.running && !data.paused) {
            indicator.removeClass().addClass('status-indicator status-running');
            statusText.text('Running');
        } else if (data.paused) {
            indicator.removeClass().addClass('status-indicator status-paused');
            statusText.text('Paused');
        } else {
            indicator.removeClass().addClass('status-indicator status-stopped');
            statusText.text('Stopped');
        }
        
        // Update statistics
        $('#timeDisplay').text(data.time);
        $('#timeScaleDisplay').text(data.timeScale);
        $('#energyDrift').text(data.energyDrift.toFixed(4) + '%');
        $('#totalParticles').text(data.particles.length);
        $('#diskStars').text(data.diskStars);
        $('#bulgeStars').text(data.bulgeStars);
        $('#habitableRegions').text(data.habitableRegions);
    }
    
    /**
     * Render loop
     */
    startRenderLoop() {
        const render = () => {
            this.render();
            requestAnimationFrame(render);
        };
        render();
    }
    
    /**
     * Main render function
     */
    render() {
        // Clear canvas
        this.ctx.fillStyle = '#000000';
        this.ctx.fillRect(0, 0, this.width, this.height);
        
        if (!this.simulationData || !this.simulationData.particles) return;
        
        // Save context state
        this.ctx.save();
        
        // Apply transformations
        this.ctx.translate(this.width / 2 + this.offsetX, this.height / 2 + this.offsetY);
        this.ctx.scale(this.zoom, this.zoom);
        
        // Draw quadtree if enabled
        if (this.showQuadtree && this.simulationData.quadtreeLines) {
            this.drawQuadtree();
        }
        
        // Draw particles
        this.drawParticles();
        
        // Restore context state
        this.ctx.restore();
        
        // Draw scale indicator
        this.drawScale();
    }
    
    /**
     * Draw particles
     */
    drawParticles() {
        const particles = this.simulationData.particles;
        const scale = 0.4; // Scale factor to fit 2000 pc into 800 pixels
        
        // Particle type colors
        const colors = [
            [0, 128, 255],    // Disk stars (blue)
            [255, 255, 0],    // Bulge stars (yellow)
            [255, 0, 0]       // SMBH (red)
        ];
        
        // Create density map for brightness
        const densityMap = this.createDensityMap(particles, scale);
        
        particles.forEach(particle => {
            const x = particle.x * scale;
            const y = particle.y * scale;
            
            // Get base color
            const color = colors[particle.type] || [255, 255, 255];
            
            // Get local density for brightness
            const gridX = Math.floor((x + 400) / 10);
            const gridY = Math.floor((y + 400) / 10);
            const density = densityMap[gridY]?.[gridX] || 1;
            const brightness = Math.min(1, 0.3 + density * 0.7);
            
            // Draw particle
            this.ctx.fillStyle = `rgba(${color[0]}, ${color[1]}, ${color[2]}, ${brightness})`;
            
            // SMBH gets special treatment
            if (particle.type === 2) {
                this.ctx.beginPath();
                this.ctx.arc(x, y, 5, 0, Math.PI * 2);
                this.ctx.fill();
                // Add glow effect
                const gradient = this.ctx.createRadialGradient(x, y, 0, x, y, 20);
                gradient.addColorStop(0, 'rgba(255, 0, 0, 0.8)');
                gradient.addColorStop(1, 'rgba(255, 0, 0, 0)');
                this.ctx.fillStyle = gradient;
                this.ctx.beginPath();
                this.ctx.arc(x, y, 20, 0, Math.PI * 2);
                this.ctx.fill();
            } else {
                // Regular particles
                const size = particle.habitable ? 3 : this.particleSize;
                
                if (particle.habitable) {
                    // Habitable regions get green highlight
                    this.ctx.fillStyle = 'rgba(0, 255, 0, 0.8)';
                }
                
                this.ctx.fillRect(x - size/2, y - size/2, size, size);
            }
        });
    }
    
    /**
     * Create density map for brightness calculation
     */
    createDensityMap(particles, scale) {
        const gridSize = 10; // 10 pixel grid cells
        const gridWidth = 80;
        const gridHeight = 80;
        const densityMap = Array(gridHeight).fill(null).map(() => Array(gridWidth).fill(0));
        
        particles.forEach(particle => {
            const x = particle.x * scale;
            const y = particle.y * scale;
            const gridX = Math.floor((x + 400) / gridSize);
            const gridY = Math.floor((y + 400) / gridSize);
            
            if (gridX >= 0 && gridX < gridWidth && gridY >= 0 && gridY < gridHeight) {
                densityMap[gridY][gridX]++;
            }
        });
        
        // Normalize density
        let maxDensity = 0;
        for (let y = 0; y < gridHeight; y++) {
            for (let x = 0; x < gridWidth; x++) {
                maxDensity = Math.max(maxDensity, densityMap[y][x]);
            }
        }
        
        if (maxDensity > 0) {
            for (let y = 0; y < gridHeight; y++) {
                for (let x = 0; x < gridWidth; x++) {
                    densityMap[y][x] /= maxDensity;
                }
            }
        }
        
        return densityMap;
    }
    
    /**
     * Draw quadtree structure
     */
    drawQuadtree() {
        if (!this.simulationData.quadtreeLines) return;
        
        this.ctx.strokeStyle = 'rgba(100, 100, 255, 0.3)';
        this.ctx.lineWidth = 0.5;
        
        const scale = 0.4;
        
        this.simulationData.quadtreeLines.forEach(line => {
            if (line.length >= 4) {
                this.ctx.beginPath();
                this.ctx.moveTo(line[0] * scale, line[1] * scale);
                this.ctx.lineTo(line[2] * scale, line[3] * scale);
                this.ctx.stroke();
            }
        });
    }
    
    /**
     * Draw scale indicator
     */
    drawScale() {
        this.ctx.save();
        
        // Draw scale bar
        const scaleLength = 100; // pixels
        const parsecs = scaleLength / (0.4 * this.zoom); // Convert to parsecs
        
        this.ctx.strokeStyle = '#ffffff';
        this.ctx.lineWidth = 2;
        this.ctx.beginPath();
        this.ctx.moveTo(20, this.height - 30);
        this.ctx.lineTo(20 + scaleLength, this.height - 30);
        this.ctx.stroke();
        
        // Draw scale text
        this.ctx.fillStyle = '#ffffff';
        this.ctx.font = '12px Arial';
        this.ctx.fillText(`${parsecs.toFixed(0)} pc`, 20, this.height - 35);
        
        this.ctx.restore();
    }
    
    /**
     * Format time scale for display
     */
    formatTimeScale(scale) {
        if (scale < 1000) {
            return scale + ' yr/s';
        } else if (scale < 1e6) {
            return (scale / 1000).toFixed(1) + ' kyr/s';
        } else if (scale < 1e9) {
            return (scale / 1e6).toFixed(1) + ' Myr/s';
        } else {
            return (scale / 1e9).toFixed(1) + ' Gyr/s';
        }
    }
}

// Initialize simulator when page loads
$(document).ready(() => {
    const simulator = new GalaxySimulator();
    console.log('Galaxy Simulator initialized');
});
