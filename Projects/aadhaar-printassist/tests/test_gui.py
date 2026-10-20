"""Optional desktop integration check; run with PRINTASSIST_GUI_TEST=1."""
import os
import time
import pytest

pytestmark = pytest.mark.skipif(os.environ.get('PRINTASSIST_GUI_TEST') != '1',
                                reason='Set PRINTASSIST_GUI_TEST=1 on an interactive desktop.')


def test_desktop_customer_lifecycle(tmp_path, pdf_factory, monkeypatch):
    import tkinter as tk
    from printassist.gui import App
    path = tmp_path/'synthetic.pdf'
    path.write_bytes(pdf_factory())
    root = tk.Tk()
    app = App(root)
    try:
        root.update()
        app.path = path
        app.name.set('Suresh Kumar')
        app.birth.set('1990')
        app.authorized.set(True)
        app.unlock()
        deadline = time.monotonic()+20
        while app.busy and time.monotonic()<deadline:
            root.update()
            time.sleep(.02)
        assert not app.busy
        assert app.job is not None
        assert app.job.report.status == 'unsigned'
        assert app.preview is not None
        assert app.name.get() == '' and app.birth.get() == ''
        assert app.customer_name == 'Suresh Kumar'
        from printassist import gui
        suggestions = []
        def cancel_save(**kwargs):
            suggestions.append(kwargs['initialfile'])
            return ''
        monkeypatch.setattr(gui.filedialog, 'asksaveasfilename', cancel_save)
        monkeypatch.setattr(gui.messagebox, 'askyesno', lambda *args: True)
        for kind in ('pdf', 'png', 'jpg', 'original', 'report'):
            app.export(kind)
        assert suggestions == ['Suresh_Kumar.pdf', 'Suresh_Kumar.png', 'Suresh_Kumar.jpg',
                               'Suresh_Kumar_original.pdf', 'Suresh_Kumar_verification.json']
        assert app.page_label.cget('text') == 'Page 1 / 1'
        app.clear_next()
        assert app.job is None and app.preview is None and not app.authorized.get()
        assert app.customer_name == ''
    finally:
        app.worker.shutdown(wait=True)
        app.busy = False
        app.close()
