"""Tk UI with serialized background PDF work and no persisted customer state."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import queue
import sys
import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from PIL import Image, ImageTk

from .document import Job, PrintSettings, atomic_write
from .errors import UserError
from .filenames import suggested_filename
from .printing import printers, print_job
from .validation import Trust


COLORS = {'valid': '#166534', 'warning': '#92400e', 'invalid': '#b91c1c', 'unsigned': '#475569'}
LABELS = {'valid': '✓ VERIFIED — configured trust and approved signer',
          'warning': '⚠ INCOMPLETE — do not treat as verified',
          'invalid': '✕ INVALID OR MODIFIED — do not treat as authentic',
          'unsigned': '○ NO EMBEDDED DOCUMENT SIGNATURE'}


def trust_directory():
    root = Path(sys.executable).parent if getattr(sys, 'frozen', False) else Path(__file__).resolve().parent.parent
    return root / 'trust'


class App:
    def __init__(self, root):
        self.root = root
        self.job = None
        self.customer_name = ''
        self.pending = []
        self.path = None
        self.page = 0
        self.busy = False
        self.preview = None
        self.worker = ThreadPoolExecutor(max_workers=1)
        self.events = queue.Queue()
        # Frozen bundles expose resources under _MEIPASS; source uses project root.
        asset_root = Path(getattr(sys, '_MEIPASS', Path(__file__).resolve().parent.parent))
        try:
            self.app_icon = tk.PhotoImage(file=str(asset_root / 'assets' / 'printassist.png'))
            self.root.iconphoto(True, self.app_icon)
            if sys.platform == 'win32':
                self.root.iconbitmap(str(asset_root / 'assets' / 'printassist.ico'))
        except tk.TclError:
            pass  # A missing optional icon must never block document handling.
        self.root.title('Aadhaar PrintAssist · Local processing')
        self.root.geometry('1080x800')
        self.root.minsize(900, 700)
        self.root.protocol('WM_DELETE_WINDOW', self.close)
        style = ttk.Style(root)
        style.theme_use('clam')
        style.configure('TButton', padding=8)
        style.configure('TLabel', padding=3)
        outer = ttk.Frame(root, padding=18)
        outer.pack(fill='both', expand=True)
        ttk.Label(outer, text='Aadhaar PrintAssist', font=('Segoe UI', 23, 'bold')).pack(anchor='w')
        ttk.Label(outer, text='Open locally • Verify the original • Prepare a print copy').pack(anchor='w')
        body = ttk.Frame(outer)
        body.pack(fill='both', expand=True, pady=14)
        left_host = ttk.Frame(body, width=480)
        left_host.pack(side='left', fill='y', padx=(0, 18))
        left_scroll = tk.Canvas(left_host, width=450, highlightthickness=0)
        left_bar = ttk.Scrollbar(left_host, orient='vertical', command=left_scroll.yview)
        left_scroll.configure(yscrollcommand=left_bar.set)
        left_bar.pack(side='right', fill='y')
        left_scroll.pack(side='left', fill='both', expand=True)
        left = ttk.Frame(left_scroll, padding=(0, 0, 8, 0))
        left_window = left_scroll.create_window((0, 0), window=left, anchor='nw')
        left.bind('<Configure>', lambda _: left_scroll.configure(scrollregion=left_scroll.bbox('all')))
        left_scroll.bind('<Configure>', lambda event: left_scroll.itemconfigure(left_window, width=event.width))
        right = ttk.Frame(body)
        right.pack(side='left', fill='both', expand=True)
        self.controls = []
        self.button(left, '1. Choose customer PDF(s)', self.choose).pack(fill='x')
        self.file_label = ttk.Label(left, text='No document selected', wraplength=420)
        self.file_label.pack(anchor='w', pady=5)
        self.trust_path = trust_directory()
        self.button(left, 'Choose administrator trust / historical evidence folder', self.choose_trust).pack(fill='x')
        self.trust_label = ttk.Label(left, text='Trust: bundled certificates and offline evidence', wraplength=420)
        self.trust_label.pack(anchor='w')
        self.name = tk.StringVar()
        self.birth = tk.StringVar()
        self.authorized = tk.BooleanVar()
        ttk.Label(left, text='2. Full English name as printed on Aadhaar').pack(anchor='w')
        self.name_entry = ttk.Entry(left, textvariable=self.name, width=42)
        self.name_entry.pack(fill='x')
        ttk.Label(left, text='Birth year or DOB (DD/MM/YYYY)').pack(anchor='w', pady=(8, 0))
        self.birth_entry = ttk.Entry(left, textvariable=self.birth)
        self.birth_entry.pack(fill='x')
        self.signed_version = tk.BooleanVar(value=False)
        version_option = ttk.Checkbutton(left, text='Use original signed version on unlock\n(exclude later changes)',
                                         variable=self.signed_version)
        version_option.pack(anchor='w', pady=(8, 0))
        self.controls.append(version_option)
        self.consent = ttk.Checkbutton(left, text='Customer authorized this document handling', variable=self.authorized)
        self.consent.pack(anchor='w', pady=10)
        self.controls.extend([self.name_entry, self.birth_entry, self.consent])
        self.button(left, '3. Unlock & verify original', self.unlock).pack(fill='x')
        self.status = tk.Label(left, text='Select a customer PDF to begin.', anchor='w', justify='left',
                               wraplength=425, font=('Segoe UI', 11, 'bold'), pady=12)
        self.status.pack(fill='x')
        self.details = tk.Text(left, height=7, width=49, wrap='word', state='disabled', font=('Segoe UI', 10))
        self.details.pack(fill='both', expand=True)
        self.show_signature_tick = tk.BooleanVar(value=True)
        tick_option = ttk.Checkbutton(left, text='Show green tick in signature area when verified',
            variable=self.show_signature_tick, command=self.refresh_preview)
        tick_option.pack(anchor='w', pady=(8, 0))
        self.controls.append(tick_option)
        self.include_badge = tk.BooleanVar(value=False)
        badge_option = ttk.Checkbutton(left, text='Include PrintAssist verification footer on print copies',
            variable=self.include_badge, command=self.refresh_preview)
        badge_option.pack(anchor='w', pady=(8, 0))
        self.controls.append(badge_option)
        settings = ttk.Frame(left)
        settings.pack(fill='x', pady=8)
        self.dpi = tk.StringVar(value='200')
        self.orientation = tk.StringVar(value='Portrait')
        self.scale = tk.StringVar(value='100')
        for label, variable, values in [('DPI', self.dpi, ('150','200','300')),
                                         ('A4', self.orientation, ('Portrait','Landscape')),
                                         ('Scale %', self.scale, ('100','90','75','50','25'))]:
            cell = ttk.Frame(settings)
            cell.pack(side='left', padx=(0, 8))
            ttk.Label(cell, text=label).pack(anchor='w')
            combo = ttk.Combobox(cell, textvariable=variable, values=values, state='readonly', width=10)
            combo.pack()
            self.controls.append(combo)
        self.button(left, 'Export A4 print PDF (all pages)', lambda: self.export('pdf')).pack(fill='x')
        row = ttk.Frame(left)
        row.pack(fill='x')
        self.button(row, 'Export JPG', lambda: self.export('jpg')).pack(side='left', expand=True, fill='x')
        self.button(row, 'Export PNG', lambda: self.export('png')).pack(side='left', expand=True, fill='x')
        self.button(left, 'Save original uploaded PDF', lambda: self.export('original')).pack(fill='x')
        self.button(left, 'Save verification report (JSON)', lambda: self.export('report')).pack(fill='x')
        self.printer = tk.StringVar()
        try:
            available = printers()
        except Exception:
            available = []
        if available:
            self.printer.set(available[0])
        printer_box = ttk.Combobox(left, textvariable=self.printer, values=available, state='readonly')
        printer_box.pack(fill='x', pady=(8, 0))
        self.controls.append(printer_box)
        self.button(left, '4. Print on Windows → clear customer', self.print_current).pack(fill='x')
        ttk.Label(right, text='Print preview — optional footer shows PrintAssist’s result for the original', wraplength=450).pack()
        self.canvas = tk.Canvas(right, background='#e2e8f0', highlightthickness=0)
        self.canvas.pack(fill='both', expand=True)
        nav = ttk.Frame(right)
        nav.pack(fill='x')
        self.button(nav, 'Previous', lambda: self.navigate(-1)).pack(side='left')
        self.page_label = ttk.Label(nav, text='')
        self.page_label.pack(side='left', expand=True)
        self.button(nav, 'Next', lambda: self.navigate(1)).pack(side='right')
        self.button(right, 'Clear customer / next queued PDF', self.clear_next).pack(fill='x', pady=8)
        ttk.Label(outer, text='Exports are unencrypted copies without the original signature. '
                  'Windows and printers may retain spool data. Clear each customer after use.',
                  wraplength=1000).pack(anchor='w')
        self.root.after(100, self.poll)

    def button(self, parent, text, command):
        widget = ttk.Button(parent, text=text, command=command)
        self.controls.append(widget)
        return widget

    def submit(self, action, done):
        if self.busy:
            return
        self.busy = True
        for widget in self.controls:
            widget.state(['disabled'])
        def work():
            try:
                value = action()
                self.events.put((done, value, None))
            except Exception as exc:
                # Only controlled UserError text is suitable for the operator.
                msg = str(exc) if isinstance(exc, UserError) else 'Operation failed. Check the PDF, destination or printer.'
                self.events.put((done, None, msg))
        self.worker.submit(work)

    def poll(self):
        try:
            done, value, error = self.events.get_nowait()
        except queue.Empty:
            pass
        else:
            self.busy = False
            for widget in self.controls:
                widget.state(['!disabled'])
            if error:
                self.status.configure(text='Operation did not complete.', fg=COLORS['warning'])
                messagebox.showerror('PrintAssist', error)
            else:
                done(value)
        self.root.after(100, self.poll)

    def settings(self):
        return PrintSettings(int(self.dpi.get()), self.orientation.get() == 'Landscape', int(self.scale.get()), self.include_badge.get(), self.show_signature_tick.get())

    def choose(self):
        selected = filedialog.askopenfilenames(title='Select customer PDFs', filetypes=[('PDF', '*.pdf')])
        if selected:
            self.pending = list(map(Path, selected))
            self.clear_next()

    def choose_trust(self):
        selected = filedialog.askdirectory(title='Administrator-managed trust and historical evidence folder')
        if selected:
            try:
                Trust.load(Path(selected))
            except UserError as error:
                messagebox.showerror('Trust configuration', str(error))
                return
            self.trust_path = Path(selected)
            current_path = self.path
            self.reset()
            self.path = current_path
            self.trust_label.configure(text='Trust folder changed; unlock the customer PDF again to apply it.')

    def reset(self):
        # All PDFium work is serialized; reset is only allowed while worker idle.
        if self.job:
            self.job.close()
        self.job = None
        self.path = None
        self.customer_name = ''
        self.name.set('')
        self.birth.set('')
        self.authorized.set(False)
        self.signed_version.set(False)
        self.preview = None
        self.canvas.delete('all')
        self.page_label.configure(text='')
        self.details.configure(state='normal')
        self.details.delete('1.0', 'end')
        self.details.configure(state='disabled')
        self.status.configure(text='Customer cleared.', fg=COLORS['unsigned'])

    def clear_next(self):
        if self.busy:
            return
        self.reset()
        if self.pending:
            self.path = self.pending.pop(0)
            self.file_label.configure(text=f'{self.path.name} · {len(self.pending)} queued')
            self.name_entry.focus_set()
        else:
            self.file_label.configure(text='No document selected')

    def unlock(self):
        if not self.path or not self.authorized.get():
            messagebox.showinfo('Customer authorization', 'Select a PDF and confirm customer authorization first.')
            return
        if self.job:
            self.job.close()
            self.job = None
        self.customer_name = ''
        self.canvas.delete('all')
        self.preview = None
        self.details.configure(state='normal')
        self.details.delete('1.0', 'end')
        self.details.configure(state='disabled')
        path, name, birth = self.path, self.name.get(), self.birth.get()
        include_badge = self.include_badge.get()
        signature_tick = self.show_signature_tick.get()
        signed_version = self.signed_version.get()
        trust_path = self.trust_path
        self.name.set('')
        self.birth.set('')
        self.status.configure(text='Opening and verifying original PDF…', fg=COLORS['unsigned'])
        def load():
            job = Job.open(path, name, birth, Trust.load(trust_path), signed_version)
            try:
                image = job.render_print_copy(0, 100, include_badge, signature_tick)
                return job, image
            except Exception:
                job.close()
                raise
        self.submit(load, lambda result: self.loaded(result, name))

    def loaded(self, result, customer_name=''):
        self.job, image = result
        self.customer_name = customer_name.strip()
        self.page = 0
        report = self.job.report
        self.status.configure(text=LABELS[report.status], fg=COLORS[report.status])
        lines = [f'Checked (UTC): {report.checked_at}', 'Checks apply to original bytes only.']
        lines.append(f'PDF parser: {report.parser_mode}')
        lines.append('Scope: original signed version (later changes excluded)' if report.scope == 'signed_revision'
                     else 'Scope: complete uploaded PDF')
        if report.diagnostic_code:
            lines.append(f'Diagnostic: {report.diagnostic_code}')
        lines.extend('\n' + warning for warning in report.warnings)
        for i, sig in enumerate(report.signatures, 1):
            lines.extend([f'\nSignature {i}: {sig.reason}', f'Signer: {sig.signer}', f'Issuer: {sig.issuer}',
                          f'Certificate: {sig.valid_from} to {sig.valid_until}',
                          f'Integrity: {sig.integrity} | Entire file: {sig.entire_file}',
                          f'Coverage: {sig.coverage} | Changes: {sig.modification_level}',
                          f'Later PDF revisions: {sig.later_revisions}',
                          f'Document revisions accepted: {sig.changes_accepted}',
                          f'Chain trusted: {sig.chain_trusted}', f'Revocation: {sig.revocation}',
                          f'Approved signer: {sig.approved_uidai_signer}'])
            history = sig.historical
            lines.extend([f'Validation basis: {sig.validation_basis}',
                          f'Historical result: {history.status.upper()} — {history.reason}',
                          f'Issuer chain signatures authenticated: {history.issuer_chain_authenticated}',
                          f'Certificate expired now: {history.certificate_expired_now}',
                          f'Signer-claimed time (not independent proof): {history.claimed_signing_time or "absent"}',
                          f'Signature timestamps: {history.signature_timestamp_tokens} | Document timestamps: {history.document_timestamps}',
                          f'Trusted historical time: {history.trusted_time or "not established"}',
                          f'Time evidence: {history.trusted_time_source or "not established"}',
                          f'Historical certificate path: {history.chain_trusted_at_proven_time}',
                          f'Historical revocation established: {history.revocation_established_at_proven_time}',
                          f'Historical diagnostic: {history.ades_subindication or ", ".join(history.reason_codes) or "none"}'])
            for evidence in history.revocation_evidence:
                if evidence.get('matches_signer_issuer'):
                    lines.append('Signer CRL: ' + str(evidence))
        lines.append('\n' + report.note)
        self.details.configure(state='normal')
        self.details.insert('1.0', '\n'.join(lines))
        self.details.configure(state='disabled')
        self.show_image(image)

    def show_image(self, image):
        image.thumbnail((max(250, self.canvas.winfo_width()-16), max(300, self.canvas.winfo_height()-16)), Image.Resampling.LANCZOS)
        self.preview = ImageTk.PhotoImage(image)
        image.close()
        self.canvas.delete('all')
        self.canvas.create_image(self.canvas.winfo_width()//2, 8, image=self.preview, anchor='n')
        self.page_label.configure(text=f'Page {self.page+1} / {len(self.job)}')

    def navigate(self, delta):
        if self.job and 0 <= self.page+delta < len(self.job):
            self.page += delta
            self.refresh_preview()

    def refresh_preview(self):
        if self.job and not self.busy:
            page, include_badge, tick = self.page, self.include_badge.get(), self.show_signature_tick.get()
            self.submit(lambda: self.job.render_print_copy(page, 100, include_badge, tick), self.show_image)

    def export(self, kind):
        if not self.job:
            messagebox.showinfo('Open PDF', 'Unlock a customer PDF first.')
            return
        if kind not in ('original', 'report') and not messagebox.askyesno(
                'Confirm unencrypted export', 'Save an unencrypted customer copy? It will remain on disk until you delete it. '
                'The original digital signature will not be retained. Customer permission is required.'):
            return
        ext = '.json' if kind == 'report' else '.pdf' if kind in ('original', 'pdf') else '.'+kind
        filename = suggested_filename(self.customer_name, kind, self.page, len(self.job))
        output = filedialog.asksaveasfilename(defaultextension=ext, initialfile=filename,
                                             filetypes=[('Output', '*'+ext)])
        if not output:
            return
        path = Path(output)
        if self.path and path.resolve() == self.path.resolve():
            messagebox.showerror('Preserve original', 'Choose a different path from the original PDF.')
            return
        settings = self.settings()
        def save():
            if kind == 'original':
                self.job.save_original(path)
            elif kind == 'report':
                raw = self.job.report.json().encode('utf-8')
                atomic_write(path, lambda f: f.write(raw))
            elif kind == 'pdf':
                self.job.export_pdf(path, settings)
            else:
                self.job.export_image(path, self.page, settings.dpi, settings.include_verification_badge, settings.show_signature_tick)
        self.submit(save, lambda _: messagebox.showinfo('Saved', 'Saved to your selected destination. Delete customer exports when no longer needed.'))

    def print_current(self):
        if not self.job:
            return
        if not self.printer.get():
            messagebox.showinfo('Printer', 'No Windows printer is available. Export an A4 PDF instead.')
            return
        status = LABELS[self.job.report.status]
        if not messagebox.askyesno('Confirm print', f'{status}\n\nPrint all pages to the selected printer? '
                                  'This is a paper copy; it carries no cryptographic signature. '
                                  'The customer session will clear after submission.'):
            return
        settings, printer = self.settings(), self.printer.get()
        self.submit(lambda: print_job(self.job, printer, settings), lambda _: self.clear_next())

    def close(self):
        if self.busy:
            messagebox.showinfo('Working', 'Wait for the current operation to finish before closing.')
            return
        self.reset()
        self.worker.shutdown(wait=False, cancel_futures=True)
        self.root.destroy()


def main():
    root = tk.Tk()
    App(root)
    root.mainloop()
