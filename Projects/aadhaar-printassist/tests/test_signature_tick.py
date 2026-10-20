import pytest
from PIL import ImageChops

from printassist.document import Job
from printassist.validation import Trust
from test_workflow import trust_for


@pytest.mark.parametrize('rotation',[0,90])
def test_tick_uses_actual_verified_widget_without_label_or_footer(tmp_path,certificates,pdf_factory,rotation):
    raw=pdf_factory(certificates[2],signature_box=(380,180,570,260),rotation=rotation)
    path=tmp_path/'input.pdf';path.write_bytes(raw)
    job=Job.open(path,'Suresh Kumar','1990',trust_for(certificates))
    try:
        sig=job.report.signatures[0]
        assert sig.page_index==0 and sig.signature_rect==(380,180,570,260)
        assert job.report.status=='valid'
        plain=job.render(0,150)
        marked=job.render_print_copy(0,150,False,True)
        assert plain.size==marked.size  # No footer, label or extra page area.
        difference=ImageChops.difference(plain,marked)
        bbox=difference.getbbox()
        assert bbox is not None
        assert bbox[2]-bbox[0]<plain.width//3
        assert bbox[3]-bbox[1]<plain.height//3
        assert (0,128,0) in set(marked.crop(bbox).getdata())
        plain.close();marked.close();difference.close()
        assert job.original==raw and path.read_bytes()==raw
    finally:
        job.close()


def test_untrusted_visible_signature_gets_no_new_tick(tmp_path,certificates,pdf_factory):
    path=tmp_path/'input.pdf'
    path.write_bytes(pdf_factory(certificates[2],signature_box=(380,180,570,260)))
    job=Job.open(path,'Suresh Kumar','1990',Trust())
    try:
        assert job.report.status=='warning'
        plain=job.render(0,150)
        marked=job.render_print_copy(0,150,False,True)
        assert ImageChops.difference(plain,marked).getbbox() is None
        plain.close();marked.close()
    finally:
        job.close()


def test_large_reference_page_can_open_with_pixel_limit_kept(tmp_path,pdf_factory):
    path=tmp_path/'input.pdf';path.write_bytes(pdf_factory(page_size=(1445,1952)))
    job=Job.open(path,'Suresh Kumar','1990',Trust())
    try:
        image=job.render(0,100);image.close()
        with pytest.raises(ValueError,match='lower DPI'):
            job.render(0,300)
    finally:
        job.close()
