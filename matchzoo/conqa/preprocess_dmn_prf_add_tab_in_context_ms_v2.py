'''
Generate *_resp_expansion_bodyText.txt and *_resp_expansion_titleText.txt with tab in the context
These files are the input files of dmn_prf (V4-1) model, which requires tab in the context

Update on 04172018 for MS_V2 data. Remove the duplicate last utterance in the context when adding the tab in context

@author: Liu Yang (yangliuyx@gmail.com / lyang@cs.umass.edu)
@homepage: https://sites.google.com/site/lyangwww/
'''
import sys
import re

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print 'please input params: data_name search_field_label'
        exit(1)
    data_name = sys.argv[1] # ms or udc or ms_v2
    search_field_label = sys.argv[2] # body or title

    basedir = '../../data/' + data_name + '/ModelInput/'

    # expected format of train/valid/test
    # label \t context seperated by \t \t candidate_response

    for data_part in list(['train', 'valid', 'test']):
        expan_file = basedir + data_part + '_resp_expansion_' + search_field_label + 'Text.txt' # label \t context \t response_expanded
        context_tab_file = basedir + data_part + '.txt' if data_name == 'udc' else basedir + data_part + '.split_context_by_tab.txt'
        with open(expan_file) as f_in1, open(context_tab_file) as f_in2, open(basedir + data_part + '_rexpand_ctab_' + search_field_label + 'Text.txt', 'w')  as f_out:
            exp_lines = f_in1.readlines()
            print 'test expan_file len(exp_lines): ', expan_file, len(exp_lines)
            l_index = 0
            for l in f_in2:
                to = l.split('\t')
                if data_name == 'ms_v2':
                    replaced = re.sub('<<<[A-Z]{4,5}>>>:', '', exp_lines[l_index].split('\t')[2]) # remove <<<AGENT>>> in the candidate response
                else:
                    replaced = exp_lines[l_index].split('\t')[2]
                f_out.write('\t'.join(to[0:len(to)-2]) + '\t' + replaced) # !remove the last duplicate utterance in the dialog context
                l_index += 1


