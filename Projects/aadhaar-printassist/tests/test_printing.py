"""Adapter contract tests; actual Windows driver checks remain separate."""
import sys
from types import SimpleNamespace
from unittest.mock import Mock

import pytest
from PIL import Image
from printassist.document import PrintSettings
from printassist.printing import print_job


@pytest.mark.parametrize('fails',[False, True])
def test_print_submission_and_failure_cleanup(monkeypatch,fails):
    import PIL
    dc = Mock()
    dc.GetDeviceCaps.side_effect = lambda cap: 1200 if cap == 8 else 1800
    if fails:
        dc.EndPage.side_effect = RuntimeError('synthetic spool failure')
    mode = SimpleNamespace(PaperSize=0, Orientation=0, Fields=0)
    spool = SimpleNamespace(OpenPrinter=Mock(return_value='handle'),
                            GetPrinter=Mock(return_value={'pDevMode':mode}),
                            DocumentProperties=Mock(return_value=1),ClosePrinter=Mock())
    native = SimpleNamespace(CreateDC=Mock(return_value='dc-handle'))
    ui = SimpleNamespace(CreateDCFromHandle=Mock(return_value=dc))
    dib = Mock()
    images = SimpleNamespace(Dib=Mock(return_value=dib))
    for name,module in [('win32print',spool),('win32gui',native),('win32ui',ui),
                        ('win32con',SimpleNamespace(DM_PAPERSIZE=2,DM_ORIENTATION=1,DM_IN_BUFFER=8,DM_OUT_BUFFER=2)),
                        ('PIL.ImageWin',images)]:
        monkeypatch.setitem(sys.modules,name,module)
    monkeypatch.setattr(PIL,'ImageWin',images,raising=False)
    monkeypatch.setattr(sys,'platform','win32')
    class Job:
        def __len__(self): return 2
        def render_print_copy(self,index,dpi,include_badge,tick): return Image.new('RGB',(200,300),'white')
    if fails:
        with pytest.raises(RuntimeError):
            print_job(Job(),'Test Printer',PrintSettings(150,True,75))
        dc.AbortDoc.assert_called_once()
        dc.EndDoc.assert_not_called()
    else:
        print_job(Job(),'Test Printer',PrintSettings(150,True,75))
        assert dib.draw.call_count == 2
        dc.EndDoc.assert_called_once()
        dc.AbortDoc.assert_not_called()
    assert mode.PaperSize == 9 and mode.Orientation == 2
    dc.DeleteDC.assert_called_once()
    spool.ClosePrinter.assert_called_once_with('handle')
