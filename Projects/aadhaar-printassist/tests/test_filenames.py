import pytest
from printassist.filenames import suggested_filename


@pytest.mark.parametrize('name,kind,expected', [
    ('Keerthi','pdf','Keerthi.pdf'),
    ('Devarakonda Keerthi','png','Devarakonda_Keerthi.png'),
    ('Keerthi','jpg','Keerthi.jpg'),
    ('Keerthi','jpeg','Keerthi.jpeg'),
    ('Keerthi','original','Keerthi_original.pdf'),
    ('Keerthi','report','Keerthi_verification.json'),
    ('../Keerthi: Test','pdf','_Keerthi__Test.pdf'),
    ('CON','pdf','_CON.pdf'),
    ('LPT1','png','_LPT1.png'),
    ('కీర్తి','png','కీర్తి.png'),
    ('  Keerthi  Reddy . ','pdf','Keerthi_Reddy.pdf'),
    ('','pdf','customer.pdf'),
])
def test_portable_customer_filename(name,kind,expected):
    assert suggested_filename(name,kind) == expected


def test_images_from_multiple_pages_have_distinct_names():
    assert suggested_filename('Keerthi','png',1,3) == 'Keerthi_page_2.png'
    assert suggested_filename('Keerthi','pdf',1,3) == 'Keerthi.pdf'


def test_unicode_filename_fits_filesystem_limits():
    assert len(suggested_filename('కీర్తి'*100,'png',9,10).encode('utf-8')) <= 200
