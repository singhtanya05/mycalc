import React, { useState, useRef } from 'react';
import { Sigma, Camera, RotateCcw, Send, Play, Sparkles, BookOpen, AlertCircle, Image as ImageIcon, ChevronDown, ChevronUp } from 'lucide-react';
import katex from 'katex';

function Latex({ math, block = false }) {
  const containerRef = useRef(null);

  React.useEffect(() => {
    if (containerRef.current) {
      try {
        // Strip out enclosing $$ or $ from the math engine if present
        let cleanMath = math.trim();
        if (cleanMath.startsWith('$$') && cleanMath.endsWith('$$')) {
          cleanMath = cleanMath.substring(2, cleanMath.length - 2);
        } else if (cleanMath.startsWith('$') && cleanMath.endsWith('$')) {
          cleanMath = cleanMath.substring(1, cleanMath.length - 1);
        }
        katex.render(cleanMath, containerRef.current, {
          displayMode: block,
          throwOnError: false,
        });
      } catch (err) {
        containerRef.current.textContent = math;
      }
    }
  }, [math, block]);

  return <span ref={containerRef} />;
}

export default function App() {
  const [equation, setEquation] = useState('');
  const [problemType, setProblemType] = useState('auto');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);
  const [showSteps, setShowSteps] = useState(false);
  
  // OCR states
  const [ocrLoading, setOcrLoading] = useState(false);
  const [imagePreview, setImagePreview] = useState(null);
  const fileInputRef = useRef(null);
  const textareaRef = useRef(null);

  const insertSymbol = (symbol) => {
    if (!textareaRef.current) return;
    const start = textareaRef.current.selectionStart;
    const end = textareaRef.current.selectionEnd;
    const text = equation;
    const newText = text.substring(0, start) + symbol + text.substring(end);
    setEquation(newText);
    
    // Put focus back and move cursor
    setTimeout(() => {
      textareaRef.current.focus();
      textareaRef.current.selectionStart = textareaRef.current.selectionEnd = start + symbol.length;
    }, 50);
  };

  const handleSolve = async (e) => {
    if (e) e.preventDefault();
    if (!equation.trim()) return;

    setLoading(true);
    setError(null);
    setShowSteps(false);
    try {
      const response = await fetch('/api/v1/solve', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ equation, problemType }),
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || errorData.message || 'Failed to solve equation');
      }

      const data = await response.json();
      setResult(data);
    } catch (err) {
      setError(err.message);
      setResult(null);
    } finally {
      setLoading(false);
    }
  };

  const handleFileChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setImagePreview(URL.createObjectURL(file));
    setOcrLoading(true);
    setError(null);

    const formData = new FormData();
    formData.append('file', file);

    try {
      const response = await fetch('/api/v1/scan', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error('Failed to parse equation from image');
      }

      const data = await response.json();
      setEquation(data.equation);
      if (data.problemType) {
        setProblemType(data.problemType);
      }
    } catch (err) {
      setError('OCR Scan failed. Please enter the equation manually.');
    } finally {
      setOcrLoading(false);
    }
  };

  const clearAll = () => {
    setEquation('');
    setResult(null);
    setError(null);
    setImagePreview(null);
  };

  const loadExample = (eq, type) => {
    setEquation(eq);
    setProblemType(type);
  };

  const mathKeys = [
    { label: 'x', value: 'x' },
    { label: 'y', value: 'y' },
    { label: '+', value: '+' },
    { label: '-', value: '-' },
    { label: '=', value: '=' },
    { label: 'd/dx', value: 'd/dx(' },
    { label: '∫', value: 'integrate ' },
    { label: 'lim', value: 'Limit(' },
    { label: '^2', value: '^2' },
    { label: '^n', value: '^' },
    { label: '√', value: 'Sqrt(' },
    { label: 'π', value: 'pi' },
    { label: 'sin', value: 'sin(' },
    { label: 'cos', value: 'cos(' },
    { label: 'tan', value: 'tan(' },
    { label: 'log', value: 'log(' },
    { label: '(', value: '(' },
    { label: ')', value: ')' },
  ];

  return (
    <div>
      <header className="app-header">
        <div className="logo">
          <Sigma className="logo-icon" size={28} />
          <span>mycalc</span>
        </div>
        <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
          Portfolio Grade Math Engine
        </div>
      </header>

      <main className="app-container">
        {/* Left column: Controls */}
        <section className="glass-card">
          <div className="card-title">
            <Sparkles size={18} style={{ color: 'var(--accent-cyan)' }} />
            Input Workspace
          </div>

          <form onSubmit={handleSolve} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
            <textarea
              ref={textareaRef}
              className="equation-textarea"
              placeholder="Type your equation (e.g. x^2 + 5x + 6 = 0 or d/dx(x^3 + 2x))"
              value={equation}
              onChange={(e) => setEquation(e.target.value)}
            />

            <div className="type-selector">
              {['auto', 'algebra', 'derivative', 'integral', 'limit'].map((type) => (
                <button
                  key={type}
                  type="button"
                  className={`type-btn ${problemType === type ? 'active' : ''}`}
                  onClick={() => setProblemType(type)}
                >
                  {type.toUpperCase()}
                </button>
              ))}
            </div>

            <div className="math-keyboard">
              {mathKeys.map((key, idx) => (
                <button
                  key={idx}
                  type="button"
                  className="key-btn"
                  onClick={() => insertSymbol(key.value)}
                >
                  {key.label}
                </button>
              ))}
            </div>

            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <button
                type="button"
                className="type-btn"
                style={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '0.5rem' }}
                onClick={clearAll}
              >
                <RotateCcw size={16} /> Clear
              </button>
              <button
                type="submit"
                disabled={loading || !equation.trim()}
                className="solve-btn"
                style={{ flex: 2 }}
              >
                {loading ? (
                  <div className="spinner" />
                ) : (
                  <>
                    <Play size={16} fill="currentColor" /> Solve Steps
                  </>
                )}
              </button>
            </div>
          </form>

          {/* Quick Examples Section */}
          <div className="ocr-section" style={{ borderTop: '1px solid var(--glass-border)', paddingTop: '1.25rem' }}>
            <div style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Sparkles size={16} style={{ color: 'var(--accent-cyan)' }} /> Try Example Equations
            </div>
            <div className="examples-grid">
              <button type="button" className="example-item-btn" onClick={() => loadExample("integrate(4*x*cos(2-3*x), x)", "integral")}>
                ∫ 4x cos(2-3x) dx
              </button>
              <button type="button" className="example-item-btn" onClick={() => loadExample("d/dx(x * sin(x))", "derivative")}>
                d/dx(x * sin(x))
              </button>
              <button type="button" className="example-item-btn" onClick={() => loadExample("Limit(x^2, x, 2)", "limit")}>
                lim (x → 2) x²
              </button>
              <button type="button" className="example-item-btn" onClick={() => loadExample("x^2 + 5x + 6 = 0", "algebra")}>
                x² + 5x + 6 = 0
              </button>
            </div>
          </div>

          {/* OCR Scanning Card Segment */}
          <div className="ocr-section">
            <div style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Camera size={16} /> Scan Equation Input
            </div>
            
            <div className="dropzone" onClick={() => fileInputRef.current?.click()}>
              {ocrLoading ? (
                <>
                  <div className="spinner" style={{ borderTopColor: 'var(--accent-cyan)' }} />
                  <span style={{ fontSize: '0.9rem' }}>Analyzing math equations in image...</span>
                </>
              ) : imagePreview ? (
                <>
                  <img src={imagePreview} alt="Equation Preview" style={{ maxHeight: '100px', borderRadius: '0.5rem' }} />
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Click to upload another image</span>
                </>
              ) : (
                <>
                  <ImageIcon size={32} style={{ color: 'var(--text-secondary)' }} />
                  <span style={{ fontWeight: 500, fontSize: '0.95rem' }}>Drag & drop or click to upload</span>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Supports PNG, JPG, JPEG equation photos</span>
                </>
              )}
            </div>
            
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              accept="image/*"
              style={{ display: 'none' }}
            />
          </div>
        </section>

        {/* Right column: Results */}
        <section className="glass-card">
          <div className="card-title">
            <BookOpen size={18} style={{ color: 'var(--accent-purple)' }} />
            Solution Steps
          </div>

          {error && (
            <div className="error-alert">
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 600, marginBottom: '0.25rem' }}>
                <AlertCircle size={18} /> Solve Error
              </div>
              {error}
            </div>
          )}

          {!result && !error && (
            <div className="empty-state">
              <Sigma size={48} style={{ color: 'var(--text-secondary)', opacity: 0.3 }} />
              <div>
                <p style={{ fontWeight: 600, color: 'var(--text-primary)' }}>No calculation active</p>
                <p style={{ fontSize: '0.9rem', marginTop: '0.25rem' }}>Type an equation, use the virtual keyboard, or scan an image to fetch step-by-step derivations.</p>
              </div>
            </div>
          )}

          {result && (
            <div className="solution-container">
              {result.finalAnswer && (
                <div className="final-answer-box" style={{ marginTop: 0 }}>
                  <div className="final-answer-title">Final Answer</div>
                  <div style={{ fontSize: '1.25rem', fontWeight: 600, overflowX: 'auto' }}>
                    <Latex math={result.latex || result.finalAnswer} block={true} />
                  </div>
                </div>
              )}

              {result.steps && result.steps.length > 0 && (
                <>
                  <button
                    type="button"
                    className="steps-toggle-btn"
                    onClick={() => setShowSteps(!showSteps)}
                  >
                    <span>{showSteps ? 'Hide Step-by-Step Derivation Sheet' : 'Show Step-by-Step Derivation Sheet'}</span>
                    {showSteps ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                  </button>

                  {showSteps && (
                    <div className="unified-paper-sheet">
                      <div className="sheet-header-title">Math Derivation Sheet</div>
                      
                      {result.steps.map((step, idx) => (
                        <div key={idx} className="paper-step-item">
                          <span className="step-header">Step {idx + 1}</span>
                          <div className="step-content-text">
                            {step.includes('$$') || step.includes('$') ? (
                              <div>
                                {step.split('$$').map((part, pIdx) => {
                                  if (pIdx % 2 === 1) {
                                    return <Latex key={pIdx} math={part} block={true} />;
                                  }
                                  return part.split('$').map((subPart, sIdx) => {
                                    if (sIdx % 2 === 1) {
                                        return <Latex key={sIdx} math={subPart} block={false} />;
                                    }
                                    return <span key={sIdx}>{subPart}</span>;
                                  });
                                })}
                              </div>
                            ) : (
                              step
                            )}
                          </div>
                        </div>
                      ))}
                      
                      {result.finalAnswer && (
                        <div className="paper-final-answer">
                          <div className="final-answer-title" style={{ color: 'var(--accent-pink)', borderBottom: '1px solid rgba(255, 117, 151, 0.15)', paddingBottom: '0.25rem', marginBottom: '0.75rem' }}>
                            Final Answer
                          </div>
                          <div style={{ fontSize: '1.3rem', fontWeight: 700, overflowX: 'auto' }}>
                            <Latex math={result.latex || result.finalAnswer} block={true} />
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                </>
              )}
            </div>
          )}
      </main>

      <footer className="app-footer">
        <p>
          Made with ❤️ by Tanya | 2026 | <Latex math="\int \text{passion} \, d(\text{code}) = \text{mycalc}" block={false} />
        </p>
      </footer>
    </div>
  );
}
