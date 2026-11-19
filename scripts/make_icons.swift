// Génère les icônes Android de Murmure (même identité que l'app macOS :
// onde douce blanche sur dégradé orange).
// Usage : swift scripts/make_icons.swift assets/images
//
// Sorties (1024 × 1024) :
//   icon.png                       icône classique, pleine (Android applique son propre masque)
//   android-icon-background.png    fond de l'icône adaptative (dégradé)
//   android-icon-foreground.png    onde seule, transparente, dans la zone sûre (66 % central)
//   android-icon-monochrome.png    onde seule pour les icônes à thème (Android 13+)
import AppKit

let outDir = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "."
let size: CGFloat = 1024

// Assombri pour que le blanc garde un contraste ≥ 3:1 sur tout le dégradé.
let orangeLight = NSColor(calibratedRed: 0.91, green: 0.447, blue: 0.11, alpha: 1) // #E8721C
let orangeDark = NSColor(calibratedRed: 0.761, green: 0.255, blue: 0.047, alpha: 1) // #C2410C

func render(_ name: String, _ draw: () -> Void) {
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(size), pixelsHigh: Int(size),
                               bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                               colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    draw()
    NSGraphicsContext.restoreGraphicsState()
    let url = URL(fileURLWithPath: outDir).appendingPathComponent(name)
    try! rep.representation(using: .png, properties: [:])!.write(to: url)
    print("écrit :", url.path)
}

func fillGradient() {
    NSGradient(colors: [orangeLight, orangeDark])!
        .draw(in: NSRect(x: 0, y: 0, width: size, height: size), angle: -60)
}

/// Onde douce : sinusoïde atténuée aux deux bouts (enveloppe gaussienne), comme un murmure.
/// `span` = part de la largeur occupée.
func wave(frequency: CGFloat, phase: CGFloat, amplitude: CGFloat, span: CGFloat) -> NSBezierPath {
    let path = NSBezierPath()
    let left = size * (0.5 - span / 2), right = size * (0.5 + span / 2)
    let steps = 240
    for i in 0...steps {
        let t = CGFloat(i) / CGFloat(steps)
        let envelope = exp(-pow((t - 0.5) / 0.30, 2))
        let y = size / 2 + amplitude * envelope * sin(2 * .pi * frequency * t + phase)
        let point = NSPoint(x: left + (right - left) * t, y: y)
        i == 0 ? path.move(to: point) : path.line(to: point)
    }
    path.lineCapStyle = .round
    path.lineJoinStyle = .round
    return path
}

/// Les 3 couches de l'app macOS (fréquence, phase, amplitude, épaisseur, opacité).
let layers: [(f: CGFloat, p: CGFloat, a: CGFloat, w: CGFloat, alpha: CGFloat)] = [
    (3.6, 2.0, 200, 18, 0.30),
    (2.4, 1.0, 180, 22, 0.50),
    (3.0, 0.0, 255, 38, 1.00),
]

func drawWaves(color: NSColor, span: CGFloat, scale: CGFloat, minAlpha: CGFloat = 0) {
    for l in layers {
        color.withAlphaComponent(max(l.alpha, minAlpha)).setStroke()
        let path = wave(frequency: l.f, phase: l.p, amplitude: l.a * scale, span: span)
        path.lineWidth = l.w * scale
        path.stroke()
    }
}

// Icône classique : le lanceur la découpe en cercle ou en carré arrondi,
// donc l'onde reste dans le cercle inscrit.
render("icon.png") {
    fillGradient()
    drawWaves(color: .white, span: 0.56, scale: 0.80)
}

render("android-icon-background.png") { fillGradient() }

// Zone sûre de l'icône adaptative : cercle de 66 % du canevas.
render("android-icon-foreground.png") {
    drawWaves(color: .white, span: 0.52, scale: 0.72)
}

// Monochrome : seule l'opacité compte ; couches pâles renforcées pour rester visibles.
render("android-icon-monochrome.png") {
    drawWaves(color: .white, span: 0.52, scale: 0.72, minAlpha: 0.45)
}
