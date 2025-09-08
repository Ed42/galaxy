const canvas = document.getElementById('galaxy-canvas');
const ctx = canvas.getContext('2d');

function draw(particles) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    particles.forEach(p => {
        ctx.beginPath();
        ctx.arc(p.x, p.y, 1, 0, 2 * Math.PI);
        ctx.fillStyle = 'white';
        ctx.fill();
    });
}

async function main() {
    const response = await fetch('/api/particles');
    const particles = await response.json();
    draw(particles);
    requestAnimationFrame(main);
}

main();
