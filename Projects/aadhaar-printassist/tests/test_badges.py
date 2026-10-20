from pathlib import Path
import pytest
from PIL import Image,ImageChops
import pypdfium2 as pdfium

from printassist.badges import STYLE,add_verification_footer
from printassist.document import Job,PrintSettings
from printassist.validation import Report,Trust,verify
from test_workflow import trust_for


@pytest.mark.parametrize('status',['valid','warning','invalid','unsigned'])
def test_footer_is_labelled_and_outside_original_content(status):
    source=Image.new('RGB',(800,400),'white')
    report=Report('a'*64,'2026-10-20T10:00:00+00:00',status=status)
    result=add_verification_footer(source,report,150)
    assert result.width==source.width and result.height>source.height
    original_area=result.crop((0,0,source.width,source.height))
    assert ImageChops.difference(original_area,source).getbbox() is None
    color=tuple(bytes.fromhex(STYLE[status][0][1:]))
    footer=result.crop((0,source.height,result.width,result.height))
    assert color in set(footer.getdata())
    if status!='valid':
        assert (22,101,52) not in set(footer.getdata())
    original_area.close();footer.close();result.close();source.close()


def test_pdf_and_image_export_badge_preserves_original_bytes(tmp_path,certificates,pdf_factory):
    raw=pdf_factory(certificates[2])
    path=tmp_path/'original.pdf';path.write_bytes(raw)
    job=Job.open(path,'Suresh Kumar','1990',trust_for(certificates))
    try:
        plain=job.render_print_copy(0,150,False)
        marked=job.render_print_copy(0,150,True)
        assert marked.height>plain.height
        plain.close();marked.close()
        for ext in ('png','jpg'):
            target=tmp_path/('copy.'+ext)
            job.export_image(target,0,150,True)
            with Image.open(target) as output:
                assert output.height>1750
        target=tmp_path/'copy.pdf'
        job.export_pdf(target,PrintSettings(150,False,100,True))
        assert verify(target.read_bytes(),'',Trust()).status=='unsigned'
        assert path.read_bytes()==raw and job.original==raw
        assert job.report.status=='valid'
    finally:
        job.close()
